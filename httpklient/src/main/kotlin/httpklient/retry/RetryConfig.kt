package no.nav.tiltakspenger.libs.httpklient

import arrow.resilience.Schedule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val excessiveRetriesLogger = KotlinLogging.logger("no.nav.tiltakspenger.libs.httpklient.RetryConfig")

/**
 * Predikat som avgjør om et nytt forsøk skal gjøres etter siste utfall. Kalles _bare_ når
 * outcome er retryable, og før [RetryConfig.schedule] spørres om retry-budsjett.
 */
typealias RetryPredicate = (RetryDecisionContext) -> Boolean

/**
 * Konfigurasjon for retry-logikk. Retry-loopen styres av en Arrow [Schedule] som bestemmer
 * timing (backoff) og antall retries; vår kode kort-slutter på [AttemptOutcome.retryable]
 * og konsulterer [retryOn] før schedulen får ordet.
 *
 * Default ([None]) gir _ingen_ retries, bakover-kompatibelt med klienter som ikke aktiverer retry.
 *
 * @property schedule Arrow [Schedule] som styrer ventetid mellom forsøk og maks antall retries.
 * @property retryOn Domene-predikat som typisk sjekker HTTP-metode-idempotens og lignende.
 * @property excessiveRetriesThreshold Hvis satt og `attempts - 1 >= terskel`, kalles
 *   [onExcessiveRetries] etter at requesten er ferdig.
 */
data class RetryConfig(
    val schedule: Schedule<AttemptOutcome, *>,
    val retryOn: RetryPredicate = NeverRetry,
    val excessiveRetriesThreshold: Int? = null,
    val onExcessiveRetries: (RetryOutcome) -> Unit = ::defaultLogExcessiveRetries,
) {
    init {
        excessiveRetriesThreshold?.let {
            require(it >= 0) { "excessiveRetriesThreshold kan ikke være negativ, var $it" }
        }
    }

    /**
     * Fluent variant for å bytte retry-predikat uten å måtte bruke `copy(...)` i konsumentkode.
     */
    fun withRetryOn(retryOn: RetryPredicate): RetryConfig {
        return copy(retryOn = retryOn)
    }

    /**
     * Slår på varsling når antall retries (`attempts - 1`) er minst [threshold].
     */
    fun notifyOnExcessiveRetries(
        threshold: Int,
        onExcessiveRetries: (RetryOutcome) -> Unit = this.onExcessiveRetries,
    ): RetryConfig {
        return copy(
            excessiveRetriesThreshold = threshold,
            onExcessiveRetries = onExcessiveRetries,
        )
    }

    /**
     * Slår av excessive-retry-varsling uten å endre schedule eller retry-predikat.
     */
    fun withoutExcessiveRetriesNotification(): RetryConfig {
        return copy(excessiveRetriesThreshold = null)
    }

    companion object {
        /** Ingen retries — default. */
        val None: RetryConfig = RetryConfig(schedule = Schedule.recurs(0L))

        /**
         * Eksponentiell backoff med valgfri jitter, opp til [maxRetries] retries.
         * Bygger `Schedule.exponential(initialDelay)` capped på [maxDelay], optionalt med moderat
         * symmetrisk jitter (`0.5..1.5`). Dette er ikke full jitter / maksimal dekorrelasjon;
         * konsumenter som trenger AWS-aktig full jitter kan sende inn en egen Arrow [Schedule]
         * direkte. Kombineres med `Schedule.recurs(maxRetries)` via `zipLeft` (dvs. behold
         * delay-output, stopp så snart en av de to schedulene sier Done).
         */
        fun exponential(
            maxRetries: Int,
            initialDelay: Duration = 200.milliseconds,
            maxDelay: Duration = 5.seconds,
            jitter: Boolean = true,
            retryOn: RetryPredicate = RetryOnServerErrorsAndNetwork,
            random: Random = Random.Default,
        ): RetryConfig {
            require(maxRetries >= 0) { "maxRetries kan ikke være negativ, var $maxRetries" }
            require(initialDelay >= Duration.ZERO) { "initialDelay kan ikke være negativ, var $initialDelay" }
            require(maxDelay >= initialDelay) { "maxDelay ($maxDelay) må være >= initialDelay ($initialDelay)" }
            val base: Schedule<AttemptOutcome, Duration> =
                Schedule.exponential<AttemptOutcome>(initialDelay)
                    .delayed { _, d -> d.coerceAtMost(maxDelay) }
            val withJitter: Schedule<AttemptOutcome, Duration> =
                if (jitter) base.jittered(0.5, 1.5, random) else base
            val limited: Schedule<AttemptOutcome, *> =
                withJitter.zipLeft(Schedule.recurs(maxRetries.toLong()))
            return RetryConfig(schedule = limited, retryOn = retryOn)
        }

        /** Konstant backoff opp til [maxRetries] retries. */
        fun fixed(
            maxRetries: Int,
            delay: Duration,
            retryOn: RetryPredicate = RetryOnServerErrorsAndNetwork,
        ): RetryConfig {
            require(maxRetries >= 0) { "maxRetries kan ikke være negativ, var $maxRetries" }
            require(delay >= Duration.ZERO) { "delay kan ikke være negativ, var $delay" }
            val schedule: Schedule<AttemptOutcome, *> =
                Schedule.spaced<AttemptOutcome>(delay)
                    .zipLeft(Schedule.recurs(maxRetries.toLong()))
            return RetryConfig(schedule = schedule, retryOn = retryOn)
        }
    }
}

/** Default predikat: aldri retry. Konsumenter må eksplisitt si hva som skal retry-es. */
val NeverRetry: RetryPredicate = { false }

/**
 * Praktisk default-predikat: tillater retry _kun_ for idempotente HTTP-metoder
 * (`GET`, `HEAD`, `OPTIONS`, `PUT`, `DELETE`). Følger RFC 7231 §4.2.2 og er på linje med
 * Failsafe sin idempotent-default. Hvilke utfall som er retry-bare (`5xx`/`429`/`Timeout`/
 * `NetworkError`) avgjøres av [AttemptOutcome.retryable].
 */
val RetryOnServerErrorsAndNetwork: RetryPredicate = { ctx -> ctx.method.isIdempotent() }

fun HttpMethod.isIdempotent(): Boolean = when (this) {
    HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.PUT, HttpMethod.DELETE -> true
    HttpMethod.POST, HttpMethod.PATCH -> false
}

/**
 * Kontekst som sendes til [RetryPredicate] etter hvert forsøk.
 *
 * @property method HTTP-metoden for requesten.
 * @property attemptNumber Forsøket som akkurat ble fullført (1-basert).
 * @property outcome Utfallet av forsøket.
 */
data class RetryDecisionContext(
    val method: HttpMethod,
    val attemptNumber: Int,
    val outcome: AttemptOutcome,
)

sealed interface AttemptOutcome {
    /**
     * `true` hvis utfallet i seg selv kan tenkes å gi et annet resultat ved nytt forsøk. Brukes
     * av retry-loopen som en hard gate — selv om [RetryConfig.retryOn] sier "ja", vil
     * loopen aldri retry-e et utfall der `retryable = false`.
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

/** Sammenfatning av retry-forbruket etter at requesten er ferdig (uansett utfall). */
data class RetryOutcome(
    val attempts: Int,
    val totalDuration: Duration,
    val attemptDurations: List<Duration>,
    val finalStatusCode: Int?,
    val finalError: HttpKlientError?,
)

internal fun defaultLogExcessiveRetries(outcome: RetryOutcome) {
    excessiveRetriesLogger.warn {
        "HTTP-klient brukte ${outcome.attempts} forsøk (totalt ${outcome.totalDuration}). " +
            "Vurder å øke timeout eller undersøke nedstrømsstabilitet."
    }
}
