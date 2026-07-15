package no.nav.tiltakspenger.libs.httpklient

import arrow.resilience.Schedule
import no.nav.tiltakspenger.libs.httpklient.retry.AttemptOutcome
import no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Retry-oppførselen til en [HttpKlient], som ren data.
 *
 * To gates er harde og kan ikke konfigureres bort:
 * - Et utfall som ikke er retryable (se [HttpKlientError.retryable]) retryes aldri — de fleste `4xx` er permanente, mens `408`/`425`/`429`/`5xx`/timeout/nettverksfeil kan retryes.
 * - `POST`/`PATCH` retryes aldri som standard: når et forsøk feiler uten respons er det ukjent om serveren rakk å behandle requesten, og et nytt forsøk kan gi doble sideeffekter.
 *   [retryIkkeIdempotente] = `true` er eksplisitt opt-in for endepunkt med dedup (f.eks. dokarkiv, som svarer `409` på duplikater).
 *
 * Default på [HttpKlientConfig] er [Ingen] — retry er en aktiv beslutning per klient, ikke noe man får stille.
 */
sealed interface Retry {
    /** Ingen retries — default. Riktig for kall der konsumenten selv eier feilhåndteringen (f.eks. utbetaling). */
    data object Ingen : Retry

    /**
     * Konstant [delay] mellom forsøk, uten jitter.
     * Finnes for paritet med appene som i dag bruker «N forsøk med konstant 1s» (journalposthendelser/meldekort/tiltak) — en migrering til [Standard] ville stille byttet dem til eksponentiell backoff.
     * [maksForsøk] teller totalt antall forsøk inkludert det første; `Fast(maksForsøk = 4)` tilsvarer ktor-ens `retryOnServerErrors(3)`.
     */
    data class Fast(
        val maksForsøk: Int = 3,
        val delay: Duration = 1.seconds,
        val retryIkkeIdempotente: Boolean = false,
    ) : Retry {
        init {
            require(maksForsøk >= 1) { "maksForsøk må være minst 1, var $maksForsøk" }
            require(delay >= Duration.ZERO) { "delay kan ikke være negativ, var $delay" }
        }
    }

    /**
     * Eksponentiell backoff fra [grunnDelay] med moderat symmetrisk jitter (0.5–1.5), hardt cappet på [maksDelay].
     * [maksForsøk] teller totalt antall forsøk inkludert det første.
     */
    data class Standard(
        val maksForsøk: Int = 3,
        val grunnDelay: Duration = 250.milliseconds,
        val maksDelay: Duration = 2.seconds,
        val retryIkkeIdempotente: Boolean = false,
    ) : Retry {
        init {
            require(maksForsøk >= 1) { "maksForsøk må være minst 1, var $maksForsøk" }
            require(grunnDelay >= Duration.ZERO) { "grunnDelay kan ikke være negativ, var $grunnDelay" }
            require(maksDelay >= grunnDelay) { "maksDelay ($maksDelay) må være >= grunnDelay ($grunnDelay)" }
        }
    }
}

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
