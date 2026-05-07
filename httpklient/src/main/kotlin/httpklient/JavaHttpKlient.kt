package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.resilience.CircuitBreaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.common.AccessToken
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.toJavaDuration

internal class JavaHttpKlient(
    private val config: HttpKlient.HttpKlientConfig,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(config.connectTimeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : HttpKlient {
    private val circuitBreakers = ConcurrentHashMap<CircuitBreakerCacheKey, CircuitBreaker>()

    override suspend fun <Response : Any> unreifiedRequest(
        uri: URI,
        method: HttpMethod,
        responseType: KType,
        build: RequestBuilder.() -> Unit,
    ): Either<HttpKlientError, HttpKlientResponse<Response>> {
        return requestStringInternal(
            RequestBuilder(uri)
                .apply(build)
                .build(method)
                .withDefaultJsonAcceptHeaderForResponse(responseType),
        ).flatMap { response -> response.toTypedResponse(responseType) }
    }

    private suspend fun requestStringInternal(
        request: BuiltHttpKlientRequest,
    ): Either<HttpKlientError, HttpKlientResponse<String>> {
        val loggingConfig = request.loggingConfig ?: config.logging
        val retryConfig = request.retryConfig ?: config.defaultRetry
        val circuitBreakerConfig = request.circuitBreakerConfig ?: config.defaultCircuitBreaker
        val baseHeaders = request.effectiveRequestHeaders()
        val authToken = resolveAuthToken(request, baseHeaders).getOrElse {
            loggingConfig.logError(request, it)
            return it.left()
        }
        val requestHeaders = if (authToken != null) baseHeaders.withBearerToken(authToken) else baseHeaders
        val preparedRequest = request
            .toJavaHttpRequest(config.defaultTimeout, requestHeaders)
            .getOrElse { return it.left() }

        val executeWithRetry = suspend {
            val retryExecution = RetryExecutor(config.clock).execute(
                request = request,
                retryConfig = retryConfig,
            ) { runSingleAttempt(preparedRequest.request) }
            finalize(
                request = request,
                preparedRequest = preparedRequest,
                requestHeaders = requestHeaders,
                loggingConfig = loggingConfig,
                retryConfig = retryConfig,
                successStatusOverride = request.successStatus,
                attempts = retryExecution.attempts,
                attemptDurations = retryExecution.attemptDurations,
                totalDuration = retryExecution.totalDuration,
                lastResult = retryExecution.lastResult,
            )
        }
        return when (circuitBreakerConfig) {
            CircuitBreakerConfig.None -> executeWithRetry()

            is CircuitBreakerConfig.Enabled -> executeWithCircuitBreaker(
                request = request,
                preparedRequest = preparedRequest,
                requestHeaders = requestHeaders,
                loggingConfig = loggingConfig,
                circuitBreakerConfig = circuitBreakerConfig,
                execute = executeWithRetry,
            )
        }
    }

    private suspend fun executeWithCircuitBreaker(
        request: BuiltHttpKlientRequest,
        preparedRequest: PreparedHttpKlientRequest,
        requestHeaders: Map<String, List<String>>,
        loggingConfig: HttpKlientLoggingConfig,
        circuitBreakerConfig: CircuitBreakerConfig.Enabled,
        execute: suspend () -> Either<HttpKlientError, HttpKlientResponse<String>>,
    ): Either<HttpKlientError, HttpKlientResponse<String>> {
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

    private suspend fun runSingleAttempt(request: java.net.http.HttpRequest): AttemptResult {
        return Either.catch {
            withContext(Dispatchers.IO) {
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
            }
        }.fold(
            ifLeft = { AttemptResult.Failure(it.toAttemptFailure()) },
            ifRight = { AttemptResult.Success(it) },
        )
    }

    private fun finalize(
        request: BuiltHttpKlientRequest,
        preparedRequest: PreparedHttpKlientRequest,
        requestHeaders: Map<String, List<String>>,
        loggingConfig: HttpKlientLoggingConfig,
        retryConfig: RetryConfig,
        successStatusOverride: ((Int) -> Boolean)?,
        attempts: Int,
        attemptDurations: List<Duration>,
        totalDuration: Duration,
        lastResult: AttemptResult,
    ): Either<HttpKlientError, HttpKlientResponse<String>> {
        return when (lastResult) {
            is AttemptResult.Failure -> {
                val error = lastResult.failure.toHttpKlientError(
                    metadata = HttpKlientMetadata(
                        rawRequestString = preparedRequest.rawRequestString,
                        rawResponseString = null,
                        requestHeaders = requestHeaders,
                        responseHeaders = emptyMap(),
                        statusCode = null,
                        attempts = attempts,
                        attemptDurations = attemptDurations,
                        totalDuration = totalDuration,
                    ),
                )
                loggingConfig.logError(request, error)
                retryConfig.notifyExcessiveRetries(attempts, totalDuration, attemptDurations, null, error)
                error.left()
            }

            is AttemptResult.Success -> {
                val response = lastResult.response
                val responseBody = response.body()
                val statusCode = response.statusCode()
                val responseHeaders = response.headers().map()
                val metadata = HttpKlientMetadata(
                    rawRequestString = preparedRequest.rawRequestString,
                    rawResponseString = responseBody,
                    requestHeaders = requestHeaders,
                    responseHeaders = responseHeaders,
                    statusCode = statusCode,
                    attempts = attempts,
                    attemptDurations = attemptDurations,
                    totalDuration = totalDuration,
                )
                if ((successStatusOverride ?: config.successStatus).invoke(statusCode)) {
                    loggingConfig.logSuccess(request, statusCode, requestHeaders, responseHeaders)
                    retryConfig.notifyExcessiveRetries(attempts, totalDuration, attemptDurations, statusCode, null)
                    HttpKlientResponse<String>(
                        statusCode = statusCode,
                        body = responseBody,
                        metadata = metadata,
                    ).right()
                } else {
                    val error = HttpKlientError.Ikke2xx(
                        statusCode = statusCode,
                        body = responseBody,
                        metadata = metadata,
                    )
                    loggingConfig.logIkke2xx(request, error)
                    retryConfig.notifyExcessiveRetries(attempts, totalDuration, attemptDurations, statusCode, error)
                    error.left()
                }
            }
        }
    }

    /**
     * Returnerer per-request token, ellers henter via [HttpKlient.HttpKlientConfig.authTokenProvider]
     * hvis satt. `Right(null)` betyr "ingen auth-header skal settes". `Left` returneres hvis
     * provideren kaster.
     */
    private suspend fun resolveAuthToken(
        request: BuiltHttpKlientRequest,
        baseHeaders: Map<String, List<String>>,
    ): Either<HttpKlientError.AuthError, AccessToken?> {
        val perRequestToken = request.authToken
        if (perRequestToken != null) return perRequestToken.right()
        val provider = config.authTokenProvider ?: return null.right<AccessToken?>()
        // Hvis konsumenten allerede har satt Authorization, hopp over provider for å unngå
        // unødvendige token-kall.
        if (baseHeaders.keys.any { it.equals("Authorization", ignoreCase = true) }) return null.right<AccessToken?>()
        return Either.catch { provider() }.mapLeft { e ->
            HttpKlientError.AuthError(
                throwable = e,
                metadata = HttpKlientMetadata(
                    rawRequestString = "${request.method} ${request.uri}",
                    rawResponseString = null,
                    requestHeaders = baseHeaders,
                    responseHeaders = emptyMap(),
                    statusCode = null,
                    attempts = 0,
                    attemptDurations = emptyList(),
                    totalDuration = Duration.ZERO,
                ),
            )
        }
    }

    /**
     * Intern sentinel som gjør en `Either.Left` om til en exception mens Arrow `CircuitBreaker`
     * beskytter blokken. Dette hviler på Arrow-kontrakten om at `protectEither` bare gjør
     * `ExecutionRejected` om til `Either.Left`, og lar andre exceptions propagere etter at de er
     * registrert som failures. Catch-blokken over fanger derfor eksplisitt bare denne typen for å
     * mappe tilbake til den opprinnelige [HttpKlientError].
     */
    private class CircuitBreakerRecordedFailure(
        val error: HttpKlientError,
    ) : RuntimeException("HTTP-feil skal telle mot circuit breaker")
}
