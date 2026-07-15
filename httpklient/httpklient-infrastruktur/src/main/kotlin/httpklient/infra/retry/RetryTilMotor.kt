package no.nav.tiltakspenger.libs.httpklient.infra.retry

import arrow.resilience.Schedule
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration

/**
 * Bygger den interne retry-motorens konfig fra den offentlige datamodellen.
 * [random] er injiserbar kun for deterministiske jitter-tester.
 *
 * Idempotens-gaten ligger i `retryOn`-predikatet motoren konsulterer per forsøk: `GET`/`PUT` regnes som idempotente (RFC 9110 §9.2.2), `POST`/`PATCH` krever `retryIkkeIdempotente`.
 */
internal fun Retry.toRetryConfig(random: Random = Random.Default): RetryConfig = when (this) {
    Retry.Ingen -> RetryConfig.None

    is Retry.Fast -> RetryConfig(
        schedule = Schedule.spaced<AttemptOutcome>(delay).zipLeft(Schedule.recurs((maksForsøk - 1).toLong())),
        retryOn = { ctx -> ctx.method.erIdempotent() || retryIkkeIdempotente },
    )

    is Retry.Standard -> RetryConfig(
        schedule = eksponentiellMedJitterOgTak(
            grunnDelay = grunnDelay,
            maksDelay = maksDelay,
            maksRetries = maksForsøk - 1,
            random = random,
        ),
        retryOn = { ctx -> ctx.method.erIdempotent() || retryIkkeIdempotente },
    )
}

/**
 * Håndrullet schedule: eksponentiell backoff der jitter og det harde [maksDelay]-taket beregnes per steg.
 *
 * Bevisst ikke bygget med Arrows `exponential().jittered().delayed(cap)`-komposisjon: `Schedule.delayed` (som `jittered` er implementert med) transformerer kun den _første_ beslutningens delay i Arrow 2.2.3 — `Decision.delayed` re-wrapper ikke continuation-steget, så fra og med andre retry forsvinner både jitter og tak når driveren følger `decision.step` (slik både vår RetryExecutor og Arrows egne drivere gjør).
 */
private fun eksponentiellMedJitterOgTak(
    grunnDelay: Duration,
    maksDelay: Duration,
    maksRetries: Int,
    random: Random,
): Schedule<AttemptOutcome, Int> {
    fun lagSteg(retryNummer: Int): suspend (AttemptOutcome) -> Schedule.Decision<AttemptOutcome, Int> = {
        if (retryNummer >= maksRetries) {
            Schedule.Decision.Done(retryNummer)
        } else {
            val eksponentiell = grunnDelay * 2.0.pow(retryNummer)
            val medJitter = eksponentiell * random.nextDouble(0.5, 1.5)
            Schedule.Decision.Continue(retryNummer + 1, minOf(medJitter, maksDelay), lagSteg(retryNummer + 1))
        }
    }
    return Schedule { input -> lagSteg(0)(input) }
}

internal fun HttpMethod.erIdempotent(): Boolean = when (this) {
    HttpMethod.GET, HttpMethod.PUT -> true
    HttpMethod.POST, HttpMethod.PATCH -> false
}
