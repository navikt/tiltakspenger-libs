package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.resilience.CircuitBreaker
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerCacheKey
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.retry.RetryExecutor
import java.net.http.HttpClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType
import kotlin.time.toJavaDuration

/**
 * `java.net.http.HttpClient`-baserte implementasjonen av [HttpKlient].
 *
 * Ansvaret er delt på flere filer for å holde hver del lesbar:
 *  - denne filen orkestrerer én request (auth-resolve → prepare → retry → circuit breaker),
 *  - [resolveAuthToken]/[effectiveAuthProvider] (InternalAuth.kt) håndterer `Authorization`,
 *  - [executeWithSkipCacheRetry] (SkipCacheRetry.kt) håndterer skip-cache-retryen,
 *  - [runSingleAttempt]/[finalize]/[executeWithCircuitBreaker] (JavaHttpKlientExecution.kt) kjører selve HTTP-kallet.
 */
internal class JavaHttpKlient(
    internal val config: HttpKlient.HttpKlientConfig,
    internal val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(config.connectTimeout.toJavaDuration())
        .followRedirects(config.followRedirects)
        .build(),
) : HttpKlient {
    /**
     * Cache av [CircuitBreaker]-instanser per [CircuitBreakerCacheKey] (dvs. per circuit breaker-`name`) for denne klienten.
     * Entries opprettes lazily og fjernes aldri; én breaker beholdes for klientens levetid per distinkt `name`.
     * `name` må derfor være lav-kardinalitet og stabil (f.eks. navnet på en nedstrøms-tjeneste) — ikke utledet fra noe høy-kardinalitet som host, tenant eller request-id, da vil mappet vokse ubegrenset.
     * Den _første_ configen for et gitt `name` vinner: cachen nøkles kun på `name` (ikke på resetTimeout/strategy/callbacks/failurePredicate), så en senere request med samme `name` men annen config gjenbruker den eksisterende breakeren og den nye configen får ingen effekt.
     * Dette er bevisst — breaker-_state_ må deles per `name` for å være meningsfull — men betyr at per-request-overstyringer av circuit breaker-config må holde `name` unikt per ønsket oppførsel.
     */
    internal val circuitBreakers = ConcurrentHashMap<CircuitBreakerCacheKey, CircuitBreaker>()

    override suspend fun <Response : Any> request(
        request: HttpKlientRequest,
        responseType: KType,
    ): Either<HttpKlientError, HttpKlientResponse<Response>> {
        return requestStringInternal(request).flatMap { response -> response.toTypedResponse(responseType) }
    }

    private suspend fun requestStringInternal(
        request: HttpKlientRequest,
    ): Either<HttpKlientError, HttpKlientResponse<String>> {
        return executeWithSkipCacheRetry(
            request = request,
            providerStyrerAuthHeader = request.effectiveAuthProvider(config) != null,
            skipCacheRetryStatuses = config.skipCacheRetryStatuses,
            loggingConfig = request.loggingConfig ?: config.logging,
        ) { skipCache -> utførMedResolvedAuth(request, skipCache) }
    }

    private suspend fun utførMedResolvedAuth(
        request: HttpKlientRequest,
        skipCache: Boolean,
    ): Either<HttpKlientError, HttpKlientResponse<String>> {
        val loggingConfig = request.loggingConfig ?: config.logging
        val retryConfig = request.retryConfig ?: config.defaultRetry
        val circuitBreakerConfig = request.circuitBreakerConfig ?: config.defaultCircuitBreaker
        val resolvedAuth = request.resolveAuthToken(config, skipCache).getOrElse {
            loggingConfig.logError(request, it)
            return it.left()
        }
        val authToken = resolvedAuth.token
        val authTidsstempler = resolvedAuth.tidsstempler
        val requestHeaders = if (authToken != null) request.headers.withBearerToken(authToken) else request.headers
        val preparedRequest = request
            .toJavaHttpRequest(config.defaultTimeout, requestHeaders, authTidsstempler)
            .getOrElse {
                // Pre-flight-feil (serialisering/ugyldig request) skal logges på lik linje med auth- og respons-feil, ikke bli stille.
                loggingConfig.logError(request, it)
                return it.left()
            }

        val effectiveSuccessStatus = request.successStatus ?: config.successStatus
        val executeWithRetry = suspend {
            val retryExecution = RetryExecutor(config.clock, config.timeSource).execute(
                request = request,
                retryConfig = retryConfig,
                isSuccessfulResponse = effectiveSuccessStatus,
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
                authTidsstempler = authTidsstempler,
                requestSendt = retryExecution.requestSendt,
                responsMottatt = retryExecution.responsMottatt,
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
                authTidsstempler = authTidsstempler,
                execute = executeWithRetry,
            )
        }
    }
}
