package no.nav.tiltakspenger.libs.httpklient

import arrow.resilience.CircuitBreaker
import kotlin.time.Duration

sealed interface HttpKlientError {
    val metadata: HttpKlientMetadata

    /**
     * `true` hvis et nytt forsøk på _samme_ request _kan_ gi et annet utfall. `false` betyr at
     * vi vet at retries er bortkastet (validerings-, serialiserings-, deserialiseringsfeil, og
     * 4xx-statuser bortsett fra `429`). Retry-loopen bruker dette som hard gate — den vil aldri
     * forsøke på nytt for `retryable = false`, uansett hva [RetryConfig.retryOn] returnerer.
     */
    val retryable: Boolean

    /**
     * Convenience-aksessorer som peker rett inn i [metadata]. Lar konsumenter slippe å skrive
     * `error.metadata.requestHeaders` osv., samtidig som vi beholder [HttpKlientMetadata] som
     * eneste datatype og sannhetskilde for disse feltene. `statusCode` eksponeres bevisst _ikke_
     * her — varianter som [Ikke2xx] og [DeserializationError] har sin egen non-null `statusCode`,
     * mens andre varianter ikke har noen status. Bruk `metadata.statusCode` hvis du trenger den
     * generiske, nullable verdien.
     */
    val rawRequestString: String get() = metadata.rawRequestString
    val rawResponseString: String? get() = metadata.rawResponseString
    val requestHeaders: Map<String, List<String>> get() = metadata.requestHeaders
    val responseHeaders: Map<String, List<String>> get() = metadata.responseHeaders
    val attempts: Int get() = metadata.attempts
    val attemptDurations: List<Duration> get() = metadata.attemptDurations
    val totalDuration: Duration get() = metadata.totalDuration

    data class Timeout(
        val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : HttpKlientError {
        override val retryable = true
    }

    data class NetworkError(
        val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : HttpKlientError {
        override val retryable = true
    }

    data class InvalidRequest(
        val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : HttpKlientError {
        override val retryable = false
    }

    data class Ikke2xx(
        val statusCode: Int,
        val body: String,
        override val metadata: HttpKlientMetadata,
    ) : HttpKlientError {
        override val retryable = isRetryableStatusCode(statusCode)
    }

    data class SerializationError(
        val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : HttpKlientError {
        override val retryable = false
    }

    data class DeserializationError(
        val throwable: Throwable,
        val body: String,
        val statusCode: Int,
        override val metadata: HttpKlientMetadata,
    ) : HttpKlientError {
        override val retryable = false
    }

    /**
     * Henting av auth-token (`HttpKlient.HttpKlientConfig.authTokenProvider`) feilet. Ikke-retryable
     * som default — token-feil er typisk enten konfig-feil (permanente) eller transient nedstrøms-
     * trøbbel som konsumenten heller bør håndtere på et høyere nivå.
     */
    data class AuthError(
        val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : HttpKlientError {
        override val retryable = false
    }

    /**
     * Requesten ble avvist fordi circuit breakeren for denne [HttpKlient]-instansen er åpen.
     * Ingen HTTP-forsøk er utført, og feilen er derfor ikke retryable i klientens interne retry-loop.
     */
    data class CircuitBreakerOpen(
        val throwable: CircuitBreaker.ExecutionRejected,
        override val metadata: HttpKlientMetadata,
    ) : HttpKlientError {
        override val retryable = false
    }
}

/**
 * 5xx og 429 regnes som mulig forbigående og dermed retryable. Alle andre statuser — inkludert
 * 4xx (utenom 429), 3xx, 2xx og 1xx — regnes som permanente i retry-sammenheng.
 */
internal fun isRetryableStatusCode(statusCode: Int): Boolean =
    statusCode == 429 || statusCode in 500..599
