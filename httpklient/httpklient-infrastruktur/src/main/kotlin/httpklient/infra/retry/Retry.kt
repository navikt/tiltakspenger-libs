package no.nav.tiltakspenger.libs.httpklient.infra.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Retry-oppførselen til en `HttpKlient`, som ren data.
 *
 * To gates er harde og kan ikke konfigureres bort:
 * - Et utfall som ikke er retryable (se [no.nav.tiltakspenger.libs.httpklient.HttpKlientError.retryable]) retryes aldri — de fleste `4xx` er permanente, mens `408`/`425`/`429`/`5xx`/timeout/nettverksfeil kan retryes.
 * - `POST`/`PATCH` retryes aldri som standard: når et forsøk feiler uten respons er det ukjent om serveren rakk å behandle requesten, og et nytt forsøk kan gi doble sideeffekter.
 *   retryIkkeIdempotente = `true` er eksplisitt opt-in for endepunkt med dedup (f.eks. dokarkiv, som svarer `409` på duplikater).
 *
 * Default på [no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig] er [Ingen] — retry er en aktiv beslutning per klient, ikke noe man får stille.
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
