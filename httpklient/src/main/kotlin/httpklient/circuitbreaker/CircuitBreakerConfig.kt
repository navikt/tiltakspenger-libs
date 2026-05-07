package no.nav.tiltakspenger.libs.httpklient

import arrow.resilience.CircuitBreaker
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
 * Konfigurasjon for circuit breaker-logikk. Default [None] gjør ingenting.
 *
 * [Enabled] bygges normalt via [count] eller [slidingWindow]. State ligger i hver
 * [no.nav.tiltakspenger.libs.httpklient.HttpKlient]-instans, ikke statisk/globalt.
 */
sealed interface CircuitBreakerConfig {
    data object None : CircuitBreakerConfig

    data class Enabled(
        val name: String,
        val resetTimeout: Duration,
        val openingStrategy: CircuitBreaker.OpeningStrategy,
        val exponentialBackoffFactor: Double,
        val maxResetTimeout: Duration,
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
            require(maxFailures > 0) { "maxFailures må være positiv, var $maxFailures" }
            return Enabled(
                name = name,
                resetTimeout = resetTimeout,
                openingStrategy = CircuitBreaker.OpeningStrategy.Count(maxFailures - 1),
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
            require(maxFailures > 0) { "maxFailures må være positiv, var $maxFailures" }
            require(windowDuration > Duration.ZERO) { "windowDuration må være positiv, var $windowDuration" }
            return Enabled(
                name = name,
                resetTimeout = resetTimeout,
                openingStrategy = CircuitBreaker.OpeningStrategy.SlidingWindow(
                    TimeSource.Monotonic,
                    windowDuration,
                    maxFailures - 1,
                ),
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
    return CircuitBreaker(
        resetTimeout = resetTimeout,
        openingStrategy = openingStrategy,
        exponentialBackoffFactor = exponentialBackoffFactor,
        maxResetTimeout = maxResetTimeout,
        timeSource = timeSource,
        onRejected = onRejected,
        onClosed = onClosed,
        onHalfOpen = onHalfOpen,
        onOpen = onOpen,
    )
}
