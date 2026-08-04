package no.nav.tiltakspenger.libs.httpklient

import java.net.URI
import kotlin.time.Duration

/**
 * Beskrivelse av en faktisk utført request/response (eller forsøk på dette).
 * Skal fylles ut eksplisitt av produsenten — det finnes ingen "fornuftige" default-verdier for denne typen informasjon, og defaults vil bare maskere bugs hvor felter ikke blir satt riktig.
 *
 * @property method HTTP-metoden requesten ble sendt med, som ren tekst (`"POST"`).
 *   Bevisst en `String` og ikke `no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod`: den typen hører til request-APIet i infrastruktur-modulen, og domenet skal ikke kjenne det.
 * @property uri URIen kallet gikk mot, komplett med query.
 *   Sammen med [method] identifiserer den endepunktet, som er den viktigste konteksten i en feillogg — men den kan bære personopplysninger, så bruk [endepunkt] når verdien skal til vanlig logg.
 * @property uriSynlighet Klientens egen vurdering av om [uri] tåler vanlig logg.
 *   Se [UriSynlighet]; styrer kun [endepunkt], aldri hva som sendes på tråden.
 * @property rawResponseString Lesbar tekst-representasjon av respons-bodyen, eller `null` når det ikke finnes noen respons.
 *   Tekstlig innhold (`Content-Type` med `text/`-prefiks, JSON eller XML — eller manglende `Content-Type`) er dekodet med charset fra `Content-Type` (default UTF-8) og trunkert ved 100 000 tegn.
 *   Binært innhold (f.eks. `application/pdf`) representeres som placeholderen `<binær respons, N bytes>` — rå binærdata havner aldri her, slik at verdien trygt kan sendes til sikkerlogg.
 * @property requestHeaders Kun headerne klienten selv setter på requesten:
 *   - konsumentens egne headere (via `headere`-parameteren på metodene),
 *   - klientens default-headere: `Accept`/`Content-Type` utledet av metoden og bodytypen (se `byggHttpKlientRequest`),
 *   - en eventuell bearer-token materialisert av klienten, i klartekst som `Authorization: Bearer ...` (bruk derfor [rawRequestString], som maskerer sensitive headere, ved logging).
 *   Bevarer innsettings-rekkefølge fra `headere`-parameteren; klientens default-headere havner til slutt.
 *   Inneholder bevisst _ikke_ transport-headerne `java.net.http.HttpClient` legger på selv ved sending — typisk `Host`, `User-Agent` (`Java-http-client/<jdk-versjon>`, f.eks. `Java-http-client/25`) og `Content-Length` (sistnevnte for body-requester).
 *   Disse settes i JDK-ens ikke-offentlige connection-lag og eksponeres ikke via `HttpRequest.headers()` (verifisert), så vi kan verken lese dem tilbake fra klienten eller speile dem her uten å reimplementere JDK-intern oppførsel — som ville bundet oss til Java-versjonen.
 * @property responseHeaders Headere fra HTTP-responsen.
 *   Rekkefølgen kommer fra JDK `HttpHeaders.map()` og er typisk alfabetisk (case-insensitiv) — ikke wire-rekkefølgen.
 * @property tidsstempler Absolutte veggklokke-tidsstempler for nøkkelpunktene i kallet (auth og request/respons).
 *   Se [HttpKlientTidsstempler].
 */
data class HttpKlientMetadata(
    val method: String,
    val uri: URI,
    val uriSynlighet: UriSynlighet,
    /**
     * Tidsbudsjettet kallet kjørte under: [Tidsgrenser.svar] per forsøk og [Tidsgrenser.oppkobling] for å få opp forbindelsen.
     * Uten dem sier ikke [totalDuration] om vi lå på grensa eller langt under den — og en timeout kan ikke navngi grensa den brøt.
     */
    val tidsgrenser: Tidsgrenser,
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
    /**
     * Absolutte veggklokke-tidsstempler for auth og request/respons.
     * Se [HttpKlientTidsstempler].
     */
    val tidsstempler: HttpKlientTidsstempler,
) {
    init {
        require(attempts >= 0) { "attempts kan ikke være negativ, var $attempts" }
    }

    /**
     * Endepunktet slik det trygt kan stå i vanlig logg, f.eks. `POST https://skjermede-personer-pip.intern.nav.no/skjermet`.
     * Dette er konteksten en feillogg trenger mest: en asynkron feil fra `java.net.http` lages på klientens egen I/O-tråd og har ingen applikasjonsframes i stacktracen, så hvilket endepunkt som feilet må stå i selve meldingen.
     * Har ikke klienten frikjent URIen ([UriSynlighet.KunSikkerlogg]), kuttes den til metode og host; hele URIen finnes uansett i [rawRequestString], som hører hjemme i sikkerlogg.
     */
    val endepunkt: String
        get() = when (uriSynlighet) {
            UriSynlighet.VanligLogg -> "$method $uri"
            UriSynlighet.KunSikkerlogg -> "$method ${uri.scheme}://${uri.host ?: "<ukjent host>"}/<skjult>"
        }

    /**
     * Maskert med vilje.
     *
     * Den genererte `toString()`-en ville lagt hele request- og respons-bodyen inn i enhver logglinje som interpolerer en [HttpKlientMetadata] eller et [HttpKlientError] — og verre: [requestHeaders] bærer `Authorization: Bearer …` i klartekst.
     * Én `log.error { "$feil" }` hos en konsument holdt til å legge både personopplysninger og et gyldig token i vanlig logg, og det er ikke noe konsumenten skal måtte huske på.
     *
     * Feltene som står igjen er trygge overalt; av headerne beholdes bare navnene, aldri verdiene.
     * Rå innhold hentes eksplisitt via [rawRequestString]/[rawResponseString] og hører kun i sikkerlogg — `loggFeil` og `loggSuksess` gjør den delingen ferdig.
     */
    override fun toString(): String =
        "HttpKlientMetadata(" +
            "endepunkt=$endepunkt, " +
            "statusCode=$statusCode, attempts=$attempts, totalDuration=$totalDuration, " +
            "requestHeaders=${requestHeaders.keys}, responseHeaders=${responseHeaders.keys}, " +
            "rawRequestString=<${rawRequestString.length} tegn, maskert>, " +
            "rawResponseString=${if (rawResponseString == null) "null" else "<${rawResponseString.length} tegn, maskert>"}" +
            ")"
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
