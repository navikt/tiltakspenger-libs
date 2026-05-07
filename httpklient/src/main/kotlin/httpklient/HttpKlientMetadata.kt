package no.nav.tiltakspenger.libs.httpklient

import kotlin.time.Duration

/**
 * Beskrivelse av en faktisk utført request/response (eller forsøk på dette). Skal fylles ut
 * eksplisitt av produsenten — det finnes ingen "fornuftige" default-verdier for denne typen
 * informasjon, og defaults vil bare maskere bugs hvor felter ikke blir satt riktig.
 *
 * @property requestHeaders Effektive request-headere som ble sendt. Bevarer innsettings-
 *   rekkefølge fra `RequestBuilder` (eventuelle default-headere som klienten har lagt til,
 *   havner til slutt).
 * @property responseHeaders Headere fra HTTP-responsen. Rekkefølgen kommer fra JDK
 *   `HttpHeaders.map()` og er typisk alfabetisk (case-insensitiv) — ikke wire-rekkefølgen.
 */
data class HttpKlientMetadata(
    val rawRequestString: String,
    val rawResponseString: String?,
    val requestHeaders: Map<String, List<String>>,
    val responseHeaders: Map<String, List<String>>,
    val statusCode: Int?,
    /** Antall forsøk som ble utført, inkludert det første. `1` betyr at det ikke ble retry-et. */
    val attempts: Int,
    /** Varighet per forsøk, i samme rekkefølge som de ble utført. */
    val attemptDurations: List<Duration>,
    /** Total veggklokketid for hele kallet, inkludert backoff mellom forsøk. */
    val totalDuration: Duration,
) {
    init {
        require(attempts >= 0) { "attempts kan ikke være negativ, var $attempts" }
    }
}
