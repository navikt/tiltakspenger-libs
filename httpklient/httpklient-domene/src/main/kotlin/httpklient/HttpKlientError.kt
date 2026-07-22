package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.resilience.CircuitBreaker
import io.github.oshai.kotlinlogging.KLogger
import no.nav.tiltakspenger.libs.logging.SE_SIKKERLOGG
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import kotlin.time.Duration

/**
 * Venstresiden i alt `httpklient` returnerer (`Either<HttpKlientError, ...>`).
 * Hver variant svarer til ett konkret feilpunkt i livssyklusen til et kall, og bevarer så mye [metadata] (rå request/respons, headere, timing, antall forsøk) som finnes på det punktet.
 *
 * ## Gruppering
 * Variantene er delt i tre under-grensesnitt langs spørsmålet **«så serveren requesten min?»** — som er det som faktisk styrer hvor trygt det er å retry-e og om kallet kan ha hatt sideeffekter:
 *
 * - [RequestIkkeSendt] — vi kom aldri så langt som til å sende noe over nettverket.
 *   Serveren har _garantert_ ikke sett requesten, så det er trygt å bygge en ny og forsøke på nytt.
 * - [IngenRespons] — et HTTP-forsøk ble startet, men vi fikk aldri en fullstendig respons.
 *   Det er _ukjent_ om serveren rakk å motta og behandle requesten (relevant for ikke-idempotente kall).
 * - [ResponsMottatt] — serveren svarte.
 *   Vi har en [ResponsMottatt.statusCode], men noe gikk likevel galt (feilstatus eller en body vi ikke klarte å deserialisere).
 *
 * Du kan matche enten på den konkrete varianten eller på gruppen, avhengig av hvor finmasket du trenger å være:
 * ```
 * when (error) {
 *     is HttpKlientError.RequestIkkeSendt -> // trygt å retry-e / logg konfig-feil
 *     is HttpKlientError.IngenRespons -> // ukjent utfall — vær varsom med ikke-idempotente kall
 *     is HttpKlientError.ResponsMottatt -> // inspiser error.statusCode
 * }
 * ```
 *
 * ## Mangler noe felles?
 * Hvis du finner deg selv i å skrive hjelpere rundt denne typen som andre konsumenter også vil trenge (f.eks. [throwableOrNull] eller felles feillogging), legg det heller her i libs enn lokalt i din app, slik at alle konsumentene deler samme oppførsel.
 */
sealed interface HttpKlientError {
    val metadata: HttpKlientMetadata

    /**
     * `true` hvis et nytt forsøk på _samme_ request _kan_ gi et annet utfall.
     * `false` betyr at vi vet at retries er bortkastet (validerings-, serialiserings-, deserialiseringsfeil, og statuser som ikke er i [isRetryableStatusCode] — det gjelder de fleste 4xx, men merk at `408`, `425` og `429` _er_ retryable).
     * Retry-loopen bruker dette som hard gate — den vil aldri forsøke på nytt for `retryable = false`, uansett retry-config.
     */
    val retryable: Boolean

    /**
     * Feil som oppsto _før_ noe ble sendt over nettverket — bygging, serialisering, auth eller en åpen circuit breaker stoppet kallet.
     * Serveren har garantert ikke sett requesten, så det er ingen risiko for sideeffekter på serversiden uansett hva konsumenten gjør videre.
     * Merk at «ingen sideeffekter» ikke betyr «alltid nyttig å forsøke på nytt umiddelbart»: [AuthError] og [CircuitBreakerOpen] har `retryable = false`, fordi et nytt forsøk på samme request typisk vil feile på samme måte til underliggende tilstand endrer seg.
     * [HttpKlientMetadata.attempts] er `0` for alle disse, og det finnes aldri en respons.
     */
    sealed interface RequestIkkeSendt : HttpKlientError {
        val throwable: Throwable
    }

    /**
     * Et HTTP-forsøk ble startet, men avsluttet uten en fullstendig respons (timeout eller nettverks-/IO-feil).
     * Det er _ukjent_ om serveren rakk å motta og behandle requesten — vær derfor varsom med blind retry av ikke-idempotente kall (`POST`/`PATCH`).
     * [throwable] er den underliggende JDK-exceptionen, allerede pakket ut av `CompletionException`/`ExecutionException`.
     */
    sealed interface IngenRespons : HttpKlientError {
        val throwable: Throwable
    }

    /**
     * Serveren returnerte en fullstendig HTTP-respons, men kallet regnes likevel som mislykket: enten fordi statusen ikke ble godtatt som suksess ([UventetStatus]) eller fordi en suksess-body ikke lot seg deserialisere ([DeserializationError]).
     * Siden serveren faktisk svarte, er både [statusCode] og en lesbar [body] alltid tilgjengelig for denne gruppen (i tillegg til [responseHeaders] fra [metadata]).
     * [body] følger samme regel som [HttpKlientMetadata.rawResponseString]: tekstlig innhold er dekodet tekst, mens binært innhold er placeholderen `<binær respons, N bytes>` — aldri rå binærdata, slik at verdien trygt kan sendes til sikkerlogg.
     */
    sealed interface ResponsMottatt : HttpKlientError {
        val statusCode: Int
        val body: String
    }

    /**
     * Forsøket time-et ut.
     * Trigges av `java.net.http.HttpTimeoutException` fra JDK-klienten — enten request-timeout (`HttpKlientConfig.timeout`) eller transportens connect-timeout (`JavaHttpTransport(connectTimeout = ...)`).
     * Forbigående, derfor `retryable = true`.
     */
    data class Timeout(
        override val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : IngenRespons {
        override val retryable = true
    }

    /**
     * Alle andre feil fra `HttpClient.sendAsync(...)` som _ikke_ er en timeout: connection refused, connection reset, DNS-oppslag som feiler, tom/malformert respons, brutt stream osv. (typisk `IOException`/`ConnectException`).
     * Forbigående, derfor `retryable = true`.
     */
    data class NetworkError(
        override val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : IngenRespons {
        override val retryable = true
    }

    /**
     * Requesten lot seg ikke bygge til en gyldig `java.net.http.HttpRequest`.
     * Trigges av `IllegalArgumentException` fra JDK sin `HttpRequest.Builder` (f.eks. et ulovlig headernavn eller en uri med et scheme som ikke er `http`/`https` — scheme-validering er delegert til JDK-klienten, ikke noe vi sjekker selv).
     * Permanent feil i konsumentens egen kode, derfor `retryable = false`.
     */
    data class InvalidRequest(
        override val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : RequestIkkeSendt {
        override val retryable = false
    }

    /**
     * Serialisering av request-body med json-modulen kastet — f.eks. en selvrefererende DTO.
     * Gjelder kun `json(...)`-bodyer; rå bodyer serialiseres ikke.
     * Permanent feil i konsumentens DTO, derfor `retryable = false`.
     */
    data class SerializationError(
        override val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : RequestIkkeSendt {
        override val retryable = false
    }

    /**
     * Serveren svarte, men [statusCode] ble _ikke_ godtatt som suksess av kallets `Statusregel` (infrastruktur-modulen) (default `Statusregel.Alle2xx`).
     *
     * Merk navnet: dette er **ikke** bokstavelig «status utenfor 2xx».
     * Statusregelen settes per kall via `godta`-parameteren, så en `2xx` kan havne her (hvis du f.eks. kun godtar `200` via `Statusregel.Eksakt`), og en non-2xx kan regnes som suksess.
     * Trenger du å skille mellom flere suksess-statuser (f.eks. `200` vs `202`), les `statusCode` på [no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse] i suksess-grenen i stedet.
     * Statuser som bærer et domeneutfall (tilgangsmaskinens `403`, dokarkivs dedup-`409`) skal _ikke_ inn i statusregelen — utled dem herfra med [harStatus] og ResponsMottatt.bodySomJson.
     *
     * Retryability følger [isRetryableStatusCode]: et utvalg statuser (408, 425, 429, 500, 502, 503, 504) er retryable, resten ikke.
     */
    data class UventetStatus(
        override val statusCode: Int,
        override val body: String,
        override val metadata: HttpKlientMetadata,
    ) : ResponsMottatt {
        override val retryable = isRetryableStatusCode(statusCode)
    }

    /**
     * Serveren svarte med en status som ble godtatt som suksess, men deserialisering av body til forventet type med Jackson (`objectMapper.readValue`) kastet.
     * [throwable] er parse-feilen og [body] er den lesbare responsen (se [ResponsMottatt]), slik at konsumenten kan inspisere/feilsøke.
     * Permanent gitt samme respons, derfor `retryable = false`.
     */
    data class DeserializationError(
        val throwable: Throwable,
        override val body: String,
        override val statusCode: Int,
        override val metadata: HttpKlientMetadata,
    ) : ResponsMottatt {
        override val retryable = false
    }

    /**
     * Henting av auth-token (`HttpKlient.HttpKlientConfig.authTokenProvider`) kastet.
     * Ingen HTTP-forsøk er utført ([HttpKlientMetadata.attempts] er `0`).
     * Ikke-retryable som default — token-feil er typisk enten konfig-feil (permanente) eller transient nedstrøms-trøbbel som konsumenten heller bør håndtere på et høyere nivå.
     */
    data class AuthError(
        override val throwable: Throwable,
        override val metadata: HttpKlientMetadata,
    ) : RequestIkkeSendt {
        override val retryable = false
    }

    /**
     * Requesten ble avvist fordi circuit breakeren for denne `HttpKlient`-instansen er åpen.
     * Trigges av Arrow Resilience sin [CircuitBreaker.ExecutionRejected].
     * Ingen HTTP-forsøk er utført ([HttpKlientMetadata.attempts] er `0`), og feilen er derfor ikke retryable i klientens interne retry-loop.
     */
    data class CircuitBreakerOpen(
        override val throwable: CircuitBreaker.ExecutionRejected,
        override val metadata: HttpKlientMetadata,
    ) : RequestIkkeSendt {
        override val retryable = false
    }
}

/**
 * Et utvalg statuser regnes som mulig forbigående og dermed retryable:
 * 408 Request Timeout, 425 Too Early, 429 Too Many Requests, 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable og 504 Gateway Timeout.
 * Alle andre statuser regnes som permanente i retry-sammenheng.
 * Public fordi den er delt sannhetskilde for retry-semantikken: både [HttpKlientError.UventetStatus.retryable] her i domenet og retry-motoren i infrastruktur-modulen bruker den.
 */
fun isRetryableStatusCode(statusCode: Int): Boolean = statusCode in retryableStatusCodes

private val retryableStatusCodes = setOf(408, 425, 429, 500, 502, 503, 504)

/**
 * Convenience-aksessorer som peker rett inn i [HttpKlientError.metadata].
 * Lar konsumenter slippe å skrive `error.metadata.requestHeaders` osv., samtidig som vi beholder [HttpKlientMetadata] som eneste datatype og sannhetskilde for disse feltene.
 * `statusCode` eksponeres bevisst _ikke_ her — kun [HttpKlientError.ResponsMottatt] har en garantert non-null `statusCode`, mens de andre gruppene ikke har noen status.
 * Bruk `metadata.statusCode` hvis du trenger den generiske, nullable verdien.
 */
val HttpKlientError.rawRequestString: String get() = metadata.rawRequestString
val HttpKlientError.rawResponseString: String? get() = metadata.rawResponseString
val HttpKlientError.requestHeaders: Map<String, List<String>> get() = metadata.requestHeaders
val HttpKlientError.responseHeaders: Map<String, List<String>> get() = metadata.responseHeaders
val HttpKlientError.attempts: Int get() = metadata.attempts
val HttpKlientError.attemptDurations: List<Duration> get() = metadata.attemptDurations
val HttpKlientError.totalDuration: Duration get() = metadata.totalDuration
val HttpKlientError.tidsstempler: HttpKlientTidsstempler get() = metadata.tidsstempler

/**
 * Den underliggende exceptionen når feilen bærer en, ellers `null`.
 * [HttpKlientError.UventetStatus] har ingen throwable (serveren svarte, bare med en uventet status), så den logges uten stacktrace.
 * Nyttig for konsumenter som logger feilen: da slipper de å gjenta denne `when`-en selv.
 */
fun HttpKlientError.throwableOrNull(): Throwable? {
    return when (this) {
        is HttpKlientError.RequestIkkeSendt -> throwable
        is HttpKlientError.IngenRespons -> throwable
        is HttpKlientError.DeserializationError -> throwable
        is HttpKlientError.UventetStatus -> null
    }
}

/**
 * Sant når feilen er en respons fra serveren med en av [statuser] — grunnmuren for å utlede domeneutfall fra feiltypen.
 * Typisk brukt sammen med HttpKlientError.ResponsMottatt.bodySomJson for statuser som bærer et strukturert svar (tilgangsmaskinens `403`, dokarkivs dedup-`409`).
 */
fun HttpKlientError.harStatus(vararg statuser: Int): Boolean =
    this is HttpKlientError.ResponsMottatt && statusCode in statuser.toSet()

/**
 * Bygger en [HttpKlientError.AuthError] for token-feil som oppstår _før_ noe kall er gjort — typisk en OBO-veksling konsumenten gjør selv (tilgangsmaskinen).
 * Gir slike feil samme form som klientens egne, slik at felles feilmapping og [loggFeil] kan gjenbrukes uten en parallell feiltype.
 */
fun authFeilUtenKall(throwable: Throwable): HttpKlientError.AuthError {
    return HttpKlientError.AuthError(
        throwable = throwable,
        metadata = HttpKlientMetadata(
            rawRequestString = "",
            rawResponseString = null,
            requestHeaders = emptyMap(),
            responseHeaders = emptyMap(),
            statusCode = null,
            attempts = 0,
            attemptDurations = emptyList(),
            totalDuration = Duration.ZERO,
            tidsstempler = HttpKlientTidsstempler.INGEN,
        ),
    )
}

/**
 * Felles feillogging for konsumenter av `httpklient`.
 *
 * Klienten logger aldri selv; logg i stedet én gang her, fra det laget som har domenekonteksten (typisk en service).
 * Slik unngås dobbeltlogging (transport-logg fra libs + domenelogg fra konsument), og det blir nøyaktig én logghendelse per feilsituasjon.
 * All HTTP-kontekst hentes fra feilen selv ([HttpKlientError.metadata]), så kalleren bidrar bare med det kalleren vet mer om enn klienten: [operasjon] og [kontekst].
 *
 * En «logghendelse» er paret [logger].error (uten personopplysninger) + `Sikkerlogg.error` (med redigert request og lesbar respons), i tråd med resten av kodebasen.
 *
 * @param logger Kallerens egen logger, slik at logglinja får kallerens navnrom.
 * @param operasjon Kort beskrivelse av hva som feilet, f.eks. `"sending til datadeling"`.
 * @param kontekst Domenekontekst som bare kalleren har, f.eks. `"Sak abc, saksnummer 123"`.
 */
fun HttpKlientError.loggFeil(
    logger: KLogger,
    operasjon: String,
    kontekst: String,
) {
    val throwable = throwableOrNull()
    val logMelding =
        "Feil ved $operasjon. $kontekst. Status: ${metadata.statusCode}, forsøk: ${metadata.attempts}. $SE_SIKKERLOGG"
    val sikkerMelding =
        "Feil ved $operasjon. $kontekst. Status: ${metadata.statusCode}, forsøk: ${metadata.attempts}, request: ${metadata.rawRequestString}. response: ${metadata.rawResponseString}. responseHeaders: ${metadata.responseHeaders}."
    if (throwable != null) {
        logger.error(throwable) { logMelding }
        Sikkerlogg.error(throwable) { sikkerMelding }
    } else {
        logger.error { logMelding }
        Sikkerlogg.error { sikkerMelding }
    }
}
