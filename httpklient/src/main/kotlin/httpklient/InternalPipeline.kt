package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerDecisionContext
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.cacheKey
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.toCircuitBreaker
import no.nav.tiltakspenger.libs.httpklient.retry.AttemptResult
import no.nav.tiltakspenger.libs.httpklient.retry.RetryExecutor
import java.time.LocalDateTime
import kotlin.time.Duration

/**
 * Selve pipelinen for ett kall: auth-resolve → request-bygging → skip-cache-retry → retry → circuit breaker → metadata.
 * Alt her er felles kode som kjører uansett transport — kun [HttpKlient.transport] rører nettverket (i [runSingleAttempt]).
 * Pipelinen logger aldri selv; alt en konsument trenger for logging ligger i [HttpKlientError]/[HttpKlientMetadata].
 */
internal suspend fun HttpKlient.utførBytesRequest(
    request: HttpKlientRequest,
): Either<HttpKlientError, HttpKlientResponse<ByteArray>> {
    return executeWithSkipCacheRetry(
        providerStyrerAuthHeader = request.effectiveAuthProvider(config) != null,
        skipCacheRetryStatuses = config.skipCacheRetryStatuses,
    ) { skipCache -> utførMedResolvedAuth(request, skipCache) }
}

private suspend fun HttpKlient.utførMedResolvedAuth(
    request: HttpKlientRequest,
    skipCache: Boolean,
): Either<HttpKlientError, HttpKlientResponse<ByteArray>> {
    val resolvedAuth = request.resolveAuthToken(config, clock, skipCache).getOrElse { return it.left() }
    val authToken = resolvedAuth.token
    val authTidsstempler = resolvedAuth.tidsstempler
    val requestHeaders = if (authToken != null) request.headers.withBearerToken(authToken) else request.headers
    val preparedRequest = request
        .toJavaHttpRequest(config.timeout, requestHeaders, authTidsstempler)
        .getOrElse { return it.left() }

    val executeWithRetry = suspend {
        val retryExecution = RetryExecutor(clock, config.timeSource).execute(
            request = request,
            retryConfig = retryConfig,
            isSuccessfulResponse = request::erSuksessStatus,
        ) { runSingleAttempt(preparedRequest.request) }
        finalize(
            request = request,
            preparedRequest = preparedRequest,
            requestHeaders = requestHeaders,
            attempts = retryExecution.attempts,
            attemptDurations = retryExecution.attemptDurations,
            totalDuration = retryExecution.totalDuration,
            lastResult = retryExecution.lastResult,
            authTidsstempler = authTidsstempler,
            requestSendt = retryExecution.requestSendt,
            responsMottatt = retryExecution.responsMottatt,
        )
    }
    return when (val circuitBreakerConfig = config.circuitBreaker) {
        CircuitBreakerConfig.None -> executeWithRetry()

        is CircuitBreakerConfig.Enabled -> executeWithCircuitBreaker(
            request = request,
            preparedRequest = preparedRequest,
            requestHeaders = requestHeaders,
            circuitBreakerConfig = circuitBreakerConfig,
            authTidsstempler = authTidsstempler,
            execute = executeWithRetry,
        )
    }
}

/**
 * Kjører retry-kjøringen ([execute]) beskyttet av en circuit breaker.
 *
 * Breakeren hentes/lages lazily fra [HttpKlient.circuitBreakers] per circuit breaker-`name`.
 * En HTTP-feil som [CircuitBreakerConfig.Enabled.failurePredicate] regner som en breaker-feil gjøres midlertidig om til en [CircuitBreakerRecordedFailure] slik at Arrow-breakeren registrerer den, og mappes så tilbake til den opprinnelige [HttpKlientError].
 * Kun det endelige resultatet etter retries registreres — beskyttelsen omslutter hele [execute].
 */
internal suspend fun HttpKlient.executeWithCircuitBreaker(
    request: HttpKlientRequest,
    preparedRequest: PreparedHttpKlientRequest,
    requestHeaders: Map<String, List<String>>,
    circuitBreakerConfig: CircuitBreakerConfig.Enabled,
    authTidsstempler: HttpKlientTidsstempler,
    execute: suspend () -> Either<HttpKlientError, HttpKlientResponse<ByteArray>>,
): Either<HttpKlientError, HttpKlientResponse<ByteArray>> {
    val circuitBreaker = circuitBreakers.computeIfAbsent(circuitBreakerConfig.cacheKey) {
        circuitBreakerConfig.toCircuitBreaker()
    }
    return try {
        circuitBreaker.protectEither {
            execute().also { result ->
                result.mapLeft { error ->
                    if (
                        circuitBreakerConfig.failurePredicate(
                            CircuitBreakerDecisionContext(request.method, request.uri, error),
                        )
                    ) {
                        throw CircuitBreakerRecordedFailure(error)
                    }
                    error
                }
            }
        }.fold(
            ifLeft = { rejected ->
                HttpKlientError.CircuitBreakerOpen(
                    throwable = rejected,
                    metadata = HttpKlientMetadata(
                        rawRequestString = preparedRequest.rawRequestString,
                        rawResponseString = null,
                        requestHeaders = requestHeaders,
                        responseHeaders = emptyMap(),
                        statusCode = null,
                        attempts = 0,
                        attemptDurations = emptyList(),
                        totalDuration = Duration.ZERO,
                        // Circuit breaker var åpen, så det ble aldri gjort et HTTP-forsøk; kun eventuelle auth-tidsstempler er relevante.
                        tidsstempler = authTidsstempler,
                    ),
                ).left()
            },
            ifRight = { it },
        )
    } catch (e: CircuitBreakerRecordedFailure) {
        e.error.left()
    }
}

/**
 * Kjører ett enkelt HTTP-forsøk via [HttpKlient.transport] og pakker resultatet i [AttemptResult].
 * En kastet exception blir en `Either.Left` med en [AttemptOutcome.Failure]; en fullstendig HTTP-respons (uansett status) blir en `Either.Right`.
 * [AssertionError] re-kastes i stedet for å mappes: den kommer aldri fra `java.net.http`, kun fra testinfra (`FakeHttpTransport` ved tom kø), og skal feile testen høylytt — ikke bli en `NetworkError` testen kanskje aldri inspiserer.
 * IO-dispatcher og rå bytes-lesing er transportens ansvar (se [JavaHttpTransport]); dekoding skjer først i [tilTypetRespons] og metadata-byggingen i [finalize].
 */
internal suspend fun HttpKlient.runSingleAttempt(request: java.net.http.HttpRequest): AttemptResult {
    return Either.catch { transport.send(request) }.mapLeft { throwable ->
        if (throwable is AssertionError) throw throwable
        throwable.toAttemptFailure()
    }
}

/**
 * Mapper det endelige [AttemptResult] fra retry-kjøringen til en [HttpKlientError] eller en vellykket [HttpKlientResponse], og bygger [HttpKlientMetadata].
 */
internal fun HttpKlient.finalize(
    request: HttpKlientRequest,
    preparedRequest: PreparedHttpKlientRequest,
    requestHeaders: Map<String, List<String>>,
    attempts: Int,
    attemptDurations: List<Duration>,
    totalDuration: Duration,
    lastResult: AttemptResult,
    authTidsstempler: HttpKlientTidsstempler,
    requestSendt: LocalDateTime,
    responsMottatt: LocalDateTime,
): Either<HttpKlientError, HttpKlientResponse<ByteArray>> {
    return lastResult.fold(
        { failure ->
            failure.toHttpKlientError(
                metadata = HttpKlientMetadata(
                    rawRequestString = preparedRequest.rawRequestString,
                    rawResponseString = null,
                    requestHeaders = requestHeaders,
                    responseHeaders = emptyMap(),
                    statusCode = null,
                    attempts = attempts,
                    attemptDurations = attemptDurations,
                    totalDuration = totalDuration,
                    // Requesten ble sendt, men vi fikk ingen (fullstendig) respons — derfor er responsMottatt null.
                    tidsstempler = authTidsstempler.copy(requestSendt = requestSendt),
                ),
            ).left()
        },
        { response ->
            val statusCode = response.statusCode
            val responseHeaders = response.headere
            // rawResponseString og UventetStatus.body skal være lesbar tekst (de havner typisk i konsumentens sikkerlogg): tekstlig innhold dekodes, binært innhold blir en placeholder.
            val lesbarResponsString = response.body.tilLesbarResponsString(responseHeaders)
            val metadata = HttpKlientMetadata(
                rawRequestString = preparedRequest.rawRequestString,
                rawResponseString = lesbarResponsString,
                requestHeaders = requestHeaders,
                responseHeaders = responseHeaders,
                statusCode = statusCode,
                attempts = attempts,
                attemptDurations = attemptDurations,
                totalDuration = totalDuration,
                tidsstempler = authTidsstempler.copy(requestSendt = requestSendt, responsMottatt = responsMottatt),
            )
            if (request.erSuksessStatus(statusCode)) {
                HttpKlientResponse<ByteArray>(
                    statusCode = statusCode,
                    body = response.body,
                    metadata = metadata,
                ).right()
            } else {
                HttpKlientError.UventetStatus(
                    statusCode = statusCode,
                    body = lesbarResponsString,
                    metadata = metadata,
                ).left()
            }
        },
    )
}

/**
 * Intern sentinel som gjør en `Either.Left` om til en exception mens Arrow `CircuitBreaker` beskytter blokken.
 * Dette hviler på Arrow-kontrakten om at `protectEither` bare gjør `ExecutionRejected` om til `Either.Left`, og lar andre exceptions propagere etter at de er registrert som failures.
 * [executeWithCircuitBreaker] fanger derfor eksplisitt bare denne typen for å mappe tilbake til den opprinnelige [HttpKlientError].
 */
internal class CircuitBreakerRecordedFailure(
    val error: HttpKlientError,
) : RuntimeException("HTTP-feil skal telle mot circuit breaker")
