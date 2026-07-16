package no.nav.tiltakspenger.libs.httpklient.infra.retry

import arrow.resilience.Schedule
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.httpklient.isRetryableStatusCode

/**
 * Predikat som avgjør om et nytt forsøk skal gjøres etter siste utfall.
 * Kalles _bare_ når outcome er retryable, og før [RetryConfig.schedule] spørres om retry-budsjett.
 */
internal typealias RetryPredicate = (RetryDecisionContext) -> Boolean

/**
 * Den interne retry-motorens konfig, bygget fra den offentlige [no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry]-datamodellen.
 * Retry-loopen styres av en Arrow [Schedule] som bestemmer timing (backoff) og antall retries; motoren kort-slutter på [AttemptOutcome.retryable] og konsulterer [retryOn] før schedulen får ordet.
 */
internal data class RetryConfig(
    val schedule: Schedule<AttemptOutcome, *>,
    val retryOn: RetryPredicate,
) {
    companion object {
        /** Ingen retries. */
        val None: RetryConfig = RetryConfig(schedule = Schedule.recurs(0L), retryOn = { false })
    }
}

/**
 * Kontekst som sendes til [RetryPredicate] etter hvert forsøk.
 *
 * @property method HTTP-metoden for requesten.
 * @property attemptNumber Forsøket som akkurat ble fullført (1-basert).
 * @property outcome Utfallet av forsøket.
 */
internal data class RetryDecisionContext(
    val method: HttpMethod,
    val attemptNumber: Int,
    val outcome: AttemptOutcome,
)

internal sealed interface AttemptOutcome {
    /**
     * `true` hvis utfallet i seg selv kan tenkes å gi et annet resultat ved nytt forsøk.
     * Brukes av retry-loopen som en hard gate — selv om [RetryConfig.retryOn] sier "ja", vil loopen aldri retry-e et utfall der `retryable = false`.
     */
    val retryable: Boolean

    /** HTTP-respons mottatt med [statusCode]. */
    data class Status(val statusCode: Int) : AttemptOutcome {
        override val retryable: Boolean get() = isRetryableStatusCode(statusCode)
    }

    /** Forsøket fullførte ikke med en HTTP-respons (nettverksfeil, timeout, osv.). */
    sealed interface Failure : AttemptOutcome {
        val throwable: Throwable
    }

    /** Forsøket time-et ut (request- eller connect-timeout). */
    data class Timeout(override val throwable: Throwable) : Failure {
        override val retryable: Boolean get() = true
    }

    /** Andre nettverks-/IO-feil. */
    data class NetworkError(override val throwable: Throwable) : Failure {
        override val retryable: Boolean get() = true
    }
}
