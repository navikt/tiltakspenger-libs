package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerDecisionContext
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.cacheKey
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.toCircuitBreaker
import no.nav.tiltakspenger.libs.httpklient.retry.AttemptResult
import no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig
import no.nav.tiltakspenger.libs.httpklient.retry.notifyExcessiveRetries
import java.net.http.HttpResponse
import java.time.LocalDateTime
import kotlin.time.Duration

/**
 * Kjører retry-kjøringen ([execute]) beskyttet av en circuit breaker.
 *
 * Breakeren hentes/lages lazily fra [JavaHttpKlient.circuitBreakers] per circuit breaker-`name`.
 * En HTTP-feil som [CircuitBreakerConfig.Enabled.failurePredicate] regner som en breaker-feil gjøres midlertidig om til en [CircuitBreakerRecordedFailure] slik at Arrow-breakeren registrerer den, og mappes så tilbake til den opprinnelige [HttpKlientError].
 * Kun det endelige resultatet etter retries registreres — beskyttelsen omslutter hele [execute].
 */
internal suspend fun JavaHttpKlient.executeWithCircuitBreaker(
    request: HttpKlientRequest,
    preparedRequest: PreparedHttpKlientRequest,
    requestHeaders: Map<String, List<String>>,
    loggingConfig: HttpKlientLoggingConfig,
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
                val error = HttpKlientError.CircuitBreakerOpen(
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
                )
                loggingConfig.logError(request, error)
                error.left()
            },
            ifRight = { it },
        )
    } catch (e: CircuitBreakerRecordedFailure) {
        e.error.left()
    }
}

/**
 * Kjører ett enkelt HTTP-forsøk på IO-dispatcheren og pakker resultatet i [AttemptResult].
 * Responsen leses som rå bytes (`BodyHandlers.ofByteArray`) slik at binært innhold (f.eks. PDF) ikke korrupteres av charset-dekoding; dekoding til tekst skjer først i [toTypedResponse] og metadata-byggingen i [finalize].
 * En kastet exception blir en `Either.Left` med en [AttemptOutcome.Failure]; et HTTP-svar (uansett status) blir en `Either.Right`.
 */
internal suspend fun JavaHttpKlient.runSingleAttempt(request: java.net.http.HttpRequest): AttemptResult {
    return Either.catch {
        withContext(Dispatchers.IO) {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
        }
    }.mapLeft { it.toAttemptFailure() }
}

/**
 * Mapper det endelige [AttemptResult] fra retry-kjøringen til en [HttpKlientError] eller en vellykket [HttpKlientResponse], bygger [HttpKlientMetadata], logger utfallet og varsler ved overdreven retry.
 */
internal fun JavaHttpKlient.finalize(
    request: HttpKlientRequest,
    preparedRequest: PreparedHttpKlientRequest,
    requestHeaders: Map<String, List<String>>,
    loggingConfig: HttpKlientLoggingConfig,
    retryConfig: RetryConfig,
    successStatusOverride: ((Int) -> Boolean)?,
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
            val error = failure.toHttpKlientError(
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
            )
            loggingConfig.logError(request, error)
            retryConfig.notifyExcessiveRetries(loggingConfig, attempts, totalDuration, attemptDurations, null, error)
            error.left()
        },
        { response ->
            val responseBody = response.body()
            val statusCode = response.statusCode()
            val responseHeaders = response.headers().map()
            // rawResponseString og UventetStatus.body skal være lesbar tekst (de havner typisk i konsumentens sikkerlogg): tekstlig innhold dekodes, binært innhold blir en placeholder.
            val lesbarResponsString = responseBody.tilLesbarResponsString(responseHeaders)
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
            if ((successStatusOverride ?: config.successStatus).invoke(statusCode)) {
                loggingConfig.logSuccess(request, statusCode, requestHeaders, responseHeaders)
                retryConfig.notifyExcessiveRetries(loggingConfig, attempts, totalDuration, attemptDurations, statusCode, null)
                HttpKlientResponse<ByteArray>(
                    statusCode = statusCode,
                    body = responseBody,
                    metadata = metadata,
                ).right()
            } else {
                val error = HttpKlientError.UventetStatus(
                    statusCode = statusCode,
                    body = lesbarResponsString,
                    metadata = metadata,
                )
                loggingConfig.logUventetStatus(request, error)
                retryConfig.notifyExcessiveRetries(loggingConfig, attempts, totalDuration, attemptDurations, statusCode, error)
                error.left()
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
