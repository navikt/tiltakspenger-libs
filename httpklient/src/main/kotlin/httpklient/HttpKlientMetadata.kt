package no.nav.tiltakspenger.libs.httpklient

import java.time.LocalDateTime
import kotlin.time.Duration

/**
 * Absolutte veggklokke-tidsstempler (fra klientens [java.time.Clock]) for nøkkelpunkter i livssyklusen til et kall.
 *
 * Utfyller de relative varighetene på [HttpKlientMetadata] ([HttpKlientMetadata.attemptDurations]/[HttpKlientMetadata.totalDuration]) med faktiske [LocalDateTime]-er.
 * Poenget er at konsumenter som må lagre «når skjedde dette» (f.eks. et oversendt-tidspunkt mot et fagsystem) kan lese det rett fra klienten i stedet for å kalle sin egen klokke ved siden av — som både dupliserer klokke-avhengigheten og gir et tidspunkt som er litt ved siden av det klienten faktisk brukte.
 *
 * Tidsstemplene har samme semantikk som `nå(clock)` (`LocalDateTime` i klokkas sone, truncated til mikrosekunder for PostgreSQL-kompatibilitet), slik at en verdi herfra er identisk med det konsumenten ville fått ved å kalle `nå(clock)` selv i samme øyeblikk — og kan lagres direkte i et `LocalDateTime`-felt uten sone-valg eller ny truncation.
 *
 * Alle feltene er nullable fordi ikke alle punkter nås i alle kall:
 * - [authStartet]/[authFullført] er `null` når ingen [AuthTokenProvider] faktisk ble kalt for requesten (per-request `bearerToken`, eksplisitt `Authorization`-header, eller ingen provider konfigurert).
 * - [requestSendt]/[responsMottatt] er `null` når det aldri ble gjort et reelt HTTP-forsøk (pre-flight-feil som bygging/serialisering/auth eller en åpen circuit breaker — jf. [HttpKlientError.RequestIkkeSendt]).
 *
 * @property authStartet Rett før [AuthTokenProvider.hentToken] ble kalt.
 * @property authFullført Rett etter at [AuthTokenProvider.hentToken] returnerte (eller kastet).
 * @property requestSendt Start på det _første_ HTTP-forsøket.
 * Ved retry ligger de påfølgende forsøkene mellom denne og [responsMottatt].
 * @property responsMottatt Slutt på det _siste_ HTTP-forsøket, men bare når det forsøket faktisk ga en respons.
 * `null` når det _endelige_ utfallet ikke ga en respons (timeout/nettverksfeil på siste forsøk), også om et tidligere forsøk fikk en respons.
 */
data class HttpKlientTidsstempler(
    val authStartet: LocalDateTime?,
    val authFullført: LocalDateTime?,
    val requestSendt: LocalDateTime?,
    val responsMottatt: LocalDateTime?,
) {
    companion object {
        /** Ingen tidsstempler registrert — korrekt verdi for feil som oppstår før noe som helst ble tidfestet. */
        val INGEN = HttpKlientTidsstempler(
            authStartet = null,
            authFullført = null,
            requestSendt = null,
            responsMottatt = null,
        )
    }
}

/**
 * Beskrivelse av en faktisk utført request/response (eller forsøk på dette).
 * Skal fylles ut eksplisitt av produsenten — det finnes ingen "fornuftige" default-verdier for denne typen informasjon, og defaults vil bare maskere bugs hvor felter ikke blir satt riktig.
 *
 * @property rawResponseString Lesbar tekst-representasjon av respons-bodyen, eller `null` når det ikke finnes noen respons.
 *   Tekstlig innhold (`Content-Type` med `text/`-prefiks, JSON eller XML — eller manglende `Content-Type`) er dekodet med charset fra `Content-Type` (default UTF-8) og trunkert ved 100 000 tegn.
 *   Binært innhold (f.eks. `application/pdf`) representeres som placeholderen `<binær respons, N bytes>` — rå binærdata havner aldri her, slik at verdien trygt kan sendes til sikkerlogg.
 * @property requestHeaders Kun headerne klienten selv setter på requesten:
 *   - konsumentens egne headere (via `headere`-parameteren på metodene),
 *   - klientens default-headere: `Accept`/`Content-Type` utledet av metoden og bodytypen (se `byggHttpKlientRequest`),
 *   - en eventuell bearer-token materialisert av klienten, i klartekst som `Authorization: Bearer ...` (bruk derfor [rawRequestString], som redakterer sensitive headere, ved logging).
 *   Bevarer innsettings-rekkefølge fra `headere`-parameteren; klientens default-headere havner til slutt.
 *   Inneholder bevisst _ikke_ transport-headerne `java.net.http.HttpClient` legger på selv ved sending — typisk `Host`, `User-Agent` (`Java-http-client/<jdk-versjon>`, f.eks. `Java-http-client/25`) og `Content-Length` (sistnevnte for body-requester).
 *   Disse settes i JDK-ens ikke-offentlige connection-lag og eksponeres ikke via `HttpRequest.headers()` (verifisert), så vi kan verken lese dem tilbake fra klienten eller speile dem her uten å reimplementere JDK-intern oppførsel — som ville bundet oss til Java-versjonen.
 * @property responseHeaders Headere fra HTTP-responsen.
 *   Rekkefølgen kommer fra JDK `HttpHeaders.map()` og er typisk alfabetisk (case-insensitiv) — ikke wire-rekkefølgen.
 * @property tidsstempler Absolutte veggklokke-tidsstempler for nøkkelpunktene i kallet (auth og request/respons). Se [HttpKlientTidsstempler].
 */
data class HttpKlientMetadata(
    val rawRequestString: String,
    val rawResponseString: String?,
    val requestHeaders: Map<String, List<String>>,
    val responseHeaders: Map<String, List<String>>,
    val statusCode: Int?,
    /**
     * Antall forsøk som ble utført, inkludert det første.
     * `1` betyr at det ikke ble retry-et, og `0` at det aldri ble gjort et HTTP-forsøk (pre-flight-feil som bygging/serialisering/auth eller en åpen circuit breaker — jf. [HttpKlientError.RequestIkkeSendt]).
     */
    val attempts: Int,
    /** Varighet per forsøk, i samme rekkefølge som de ble utført, målt monotont via klientens `timeSource` (ikke mot veggklokka). */
    val attemptDurations: List<Duration>,
    /** Total tid for hele kallet, inkludert backoff mellom forsøk, målt monotont via klientens `timeSource` (immun mot klokkejustering). */
    val totalDuration: Duration,
    /** Absolutte veggklokke-tidsstempler for auth og request/respons. Se [HttpKlientTidsstempler]. */
    val tidsstempler: HttpKlientTidsstempler,
) {
    init {
        require(attempts >= 0) { "attempts kan ikke være negativ, var $attempts" }
    }
}

/**
 * Henter alle verdier for response-headeren [name], case-insensitivt (HTTP-headere er case-insensitive).
 * Returnerer tom liste hvis headeren mangler.
 */
fun HttpKlientMetadata.responseHeaderValues(name: String): List<String> =
    responseHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()

/**
 * Henter første verdi for response-headeren [name], case-insensitivt, eller `null` hvis headeren mangler.
 */
fun HttpKlientMetadata.responseHeader(name: String): String? =
    responseHeaderValues(name).firstOrNull()

/**
 * Henter alle verdier for request-headeren [name], case-insensitivt.
 * Returnerer tom liste hvis headeren mangler.
 */
fun HttpKlientMetadata.requestHeaderValues(name: String): List<String> =
    requestHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()

/**
 * Henter første verdi for request-headeren [name], case-insensitivt, eller `null` hvis headeren mangler.
 */
fun HttpKlientMetadata.requestHeader(name: String): String? =
    requestHeaderValues(name).firstOrNull()
