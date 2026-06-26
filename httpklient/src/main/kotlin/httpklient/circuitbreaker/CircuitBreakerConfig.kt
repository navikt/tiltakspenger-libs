package no.nav.tiltakspenger.libs.httpklient.circuitbreaker

import arrow.resilience.CircuitBreaker
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpMethod
import java.net.URI
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Predikat som avgjør om en ferdig HTTP-feil skal telle som circuit breaker-feil.
 * Kalles etter eventuell retry, slik at circuit breakeren ser sluttresultatet for requesten.
 */
typealias CircuitBreakerPredicate = (CircuitBreakerDecisionContext) -> Boolean

/** Kontekst som sendes til [CircuitBreakerPredicate]. */
data class CircuitBreakerDecisionContext(
    val method: HttpMethod,
    val uri: URI,
    val error: HttpKlientError,
)

/** Default predikat: tell bare forbigående/retryable feil mot circuit breakeren. */
val CircuitBreakerOnRetryableErrors: CircuitBreakerPredicate = { ctx -> ctx.error.retryable }

/** Predikat for konsumenter som vil skru av failure-recording uten å skru av configen. */
val NeverRecordCircuitBreakerFailure: CircuitBreakerPredicate = { false }

private val NoopCircuitBreakerCallback: suspend () -> Unit = {}

/**
 * Strategi for når circuit breakeren skal åpne.
 * Bevisst tidskilde-fri: tidskilden settes ett sted ([CircuitBreakerConfig.Enabled.timeSource]) og bakes inn i Arrow-strategien først i [toCircuitBreaker].
 * Da finnes det nøyaktig én tidskilde, og strategien kan aldri komme i utakt med breakerens tidskilde.
 */
sealed interface CircuitBreakerOpeningStrategy {
    /** Åpner når [maxFailures] sammenhengende feil er registrert. */
    data class Count(val maxFailures: Int) : CircuitBreakerOpeningStrategy

    /** Åpner når [maxFailures] feil er registrert innenfor [windowDuration]. */
    data class SlidingWindow(val maxFailures: Int, val windowDuration: Duration) : CircuitBreakerOpeningStrategy
}

/**
 * Konfigurasjon for circuit breaker-logikk.
 * Default [None] gjør ingenting.
 *
 * [Enabled] bygges normalt via [count] eller [slidingWindow].
 * State ligger i hver [no.nav.tiltakspenger.libs.httpklient.HttpKlient]-instans, ikke statisk/globalt, og caches per [Enabled.name] for klientens levetid.
 * [Enabled.name] må derfor være lav-kardinalitet og stabil (typisk navnet på en nedstrøms-tjeneste); ikke utled den fra host, tenant eller request-id.
 *
 * [Enabled] er bygget på Arrow Resilience sin [CircuitBreaker] og eksponerer bevisst [TimeSource] direkte.
 * Det er akseptert her fordi alle konsumenter i teamet allerede bruker Arrow, og det gir tilgang til hele Arrow Resilience-konfigurasjonen uten et eget innpakningslag.
 */
sealed interface CircuitBreakerConfig {
    data object None : CircuitBreakerConfig

    data class Enabled(
        /**
         * Stabilt, lav-kardinalitet navn som identifiserer breaker-state innenfor én [no.nav.tiltakspenger.libs.httpklient.HttpKlient]-instans.
         * Requests med samme navn deler breaker; instansen caches for klientens levetid, så ikke bruk høy-kardinalitet-verdier (host/tenant/request-id).
         */
        val name: String,
        val resetTimeout: Duration,
        val openingStrategy: CircuitBreakerOpeningStrategy,
        val exponentialBackoffFactor: Double,
        val maxResetTimeout: Duration,
        /**
         * Tidskilde for all circuit breaker-timing (reset-timeout og sliding window).
         * Primært ment for deterministiske tester (f.eks. [kotlin.time.TestTimeSource]); bruk standarden [TimeSource.Monotonic] i produksjon.
         */
        val timeSource: TimeSource,
        val failurePredicate: CircuitBreakerPredicate,
        val onRejected: suspend () -> Unit,
        val onClosed: suspend () -> Unit,
        val onHalfOpen: suspend () -> Unit,
        val onOpen: suspend () -> Unit,
    ) : CircuitBreakerConfig {
        init {
            require(name.isNotBlank()) { "name kan ikke være blank" }
            require(resetTimeout > Duration.ZERO) { "resetTimeout må være positiv, var $resetTimeout" }
            require(exponentialBackoffFactor >= 1.0) {
                "exponentialBackoffFactor må være >= 1.0, var $exponentialBackoffFactor"
            }
            require(maxResetTimeout >= resetTimeout) {
                "maxResetTimeout ($maxResetTimeout) må være >= resetTimeout ($resetTimeout)"
            }
            // Enabled er public og kan instansieres direkte (også via copy), så strategien valideres her i stedet for kun i factory-metodene.
            // Ugyldige verdier (f.eks. maxFailures = 0) ville ellers gitt negativ Arrow-konfig (maxFailures - 1) i toCircuitBreaker.
            when (val strategy = openingStrategy) {
                is CircuitBreakerOpeningStrategy.Count ->
                    require(strategy.maxFailures > 0) { "maxFailures må være positiv, var ${strategy.maxFailures}" }

                is CircuitBreakerOpeningStrategy.SlidingWindow -> {
                    require(strategy.maxFailures > 0) { "maxFailures må være positiv, var ${strategy.maxFailures}" }
                    require(strategy.windowDuration > Duration.ZERO) { "windowDuration må være positiv, var ${strategy.windowDuration}" }
                }
            }
        }

        fun withFailurePredicate(failurePredicate: CircuitBreakerPredicate): Enabled {
            return copy(failurePredicate = failurePredicate)
        }

        fun withExponentialBackoff(
            factor: Double,
            maxResetTimeout: Duration = this.maxResetTimeout,
        ): Enabled {
            return copy(exponentialBackoffFactor = factor, maxResetTimeout = maxResetTimeout)
        }

        /** Bytter tidskilde; primært for deterministiske tester. */
        fun withTimeSource(timeSource: TimeSource): Enabled {
            return copy(timeSource = timeSource)
        }

        fun doOnRejectedTask(onRejected: suspend () -> Unit): Enabled {
            return copy(onRejected = onRejected)
        }

        fun doOnClosed(onClosed: suspend () -> Unit): Enabled {
            return copy(onClosed = onClosed)
        }

        fun doOnHalfOpen(onHalfOpen: suspend () -> Unit): Enabled {
            return copy(onHalfOpen = onHalfOpen)
        }

        fun doOnOpen(onOpen: suspend () -> Unit): Enabled {
            return copy(onOpen = onOpen)
        }
    }

    companion object {
        fun count(
            name: String,
            maxFailures: Int,
            resetTimeout: Duration,
            failurePredicate: CircuitBreakerPredicate = CircuitBreakerOnRetryableErrors,
        ): Enabled {
            // maxFailures valideres i Enabled.init, så vi unngår å duplisere require-en her.
            return Enabled(
                name = name,
                resetTimeout = resetTimeout,
                openingStrategy = CircuitBreakerOpeningStrategy.Count(maxFailures),
                exponentialBackoffFactor = 1.0,
                maxResetTimeout = resetTimeout,
                timeSource = TimeSource.Monotonic,
                failurePredicate = failurePredicate,
                onRejected = NoopCircuitBreakerCallback,
                onClosed = NoopCircuitBreakerCallback,
                onHalfOpen = NoopCircuitBreakerCallback,
                onOpen = NoopCircuitBreakerCallback,
            )
        }

        fun slidingWindow(
            name: String,
            maxFailures: Int,
            windowDuration: Duration,
            resetTimeout: Duration,
            failurePredicate: CircuitBreakerPredicate = CircuitBreakerOnRetryableErrors,
        ): Enabled {
            // maxFailures og windowDuration valideres i Enabled.init, så vi unngår å duplisere require-ene her.
            return Enabled(
                name = name,
                resetTimeout = resetTimeout,
                openingStrategy = CircuitBreakerOpeningStrategy.SlidingWindow(maxFailures, windowDuration),
                exponentialBackoffFactor = 1.0,
                maxResetTimeout = resetTimeout,
                timeSource = TimeSource.Monotonic,
                failurePredicate = failurePredicate,
                onRejected = NoopCircuitBreakerCallback,
                onClosed = NoopCircuitBreakerCallback,
                onHalfOpen = NoopCircuitBreakerCallback,
                onOpen = NoopCircuitBreakerCallback,
            )
        }
    }
}

@JvmInline
internal value class CircuitBreakerCacheKey(val name: String)

internal val CircuitBreakerConfig.Enabled.cacheKey: CircuitBreakerCacheKey get() = CircuitBreakerCacheKey(name)

internal fun CircuitBreakerConfig.Enabled.toCircuitBreaker(): CircuitBreaker {
    val arrowOpeningStrategy = when (val strategy = openingStrategy) {
        // Arrow teller "tillatte ekstra feil utover den første", så vi trekker fra 1 fra vår brukervendte maxFailures.
        is CircuitBreakerOpeningStrategy.Count ->
            CircuitBreaker.OpeningStrategy.Count(strategy.maxFailures - 1)

        // Arrow lagrer en timeSource på SlidingWindow, men leser den aldri: selve vinduet timestampes via CircuitBreaker.timeSource (markNow() i trackFailure).
        // Vi sender likevel inn vår [timeSource] her for å ikke etterlate en vilkårlig/avvikende verdi, og for å være robust mot framtidige Arrow-versjoner som måtte begynne å lese den.
        is CircuitBreakerOpeningStrategy.SlidingWindow ->
            CircuitBreaker.OpeningStrategy.SlidingWindow(timeSource, strategy.windowDuration, strategy.maxFailures - 1)
    }
    return CircuitBreaker(
        resetTimeout = resetTimeout,
        openingStrategy = arrowOpeningStrategy,
        exponentialBackoffFactor = exponentialBackoffFactor,
        maxResetTimeout = maxResetTimeout,
        timeSource = timeSource,
        onRejected = onRejected,
        onClosed = onClosed,
        onHalfOpen = onHalfOpen,
        onOpen = onOpen,
    )
}
