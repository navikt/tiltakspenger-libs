package no.nav.tiltakspenger.libs.httpklient.infra.circuitbreaker

import arrow.resilience.CircuitBreaker

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
