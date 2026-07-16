package no.nav.tiltakspenger.libs.httpklient.infra

import arrow.core.Either
import arrow.core.flatMap
import arrow.resilience.CircuitBreaker
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.httpklient.infra.circuitbreaker.CircuitBreakerCacheKey
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Header
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.retry.RetryConfig
import no.nav.tiltakspenger.libs.httpklient.infra.retry.toRetryConfig
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Felles HTTP-klient for tiltakspenger-tjenester: én statisk typet metode per reelt behov, med innebygd auth, timeout, retry, circuit breaker og en enhetlig feiltype ([HttpKlientError]).
 *
 * `Content-Type`, `Accept` og (de)serialisering er en intern konsekvens av hvilken metode du kaller — det finnes ingen content-type-strenger, body-varianter eller respons-typemagi i API-et.
 * Klienten logger aldri selv; bruk [HttpKlientError.loggFeil] og HttpKlientResponse.loggTilSikkerlogg fra laget som har domenekonteksten.
 *
 * Dette er en `final` klasse uten interface: den eneste sømmen er [transport], og tester bytter den mot `FakeHttpTransport` (testFixtures) slik at hele den reelle pipelinen — auth-materialisering, retry-gates, statusregler, dekoding, metadata og redaksjon — kjører for ekte i test i stedet for å emuleres.
 *
 * De reified metodene er tynne inline-fasader som kun fanger typeargumentet; all logikk ligger i `internal`-broene under, slik at interne typer ikke lekker inn i public inline-bytecode.
 *
 * ## Mangler noe felles?
 * Trenger du en metode eller hjelper som andre konsumenter også vil ha nytte av (nytt bodyformat, hjelpere rundt [HttpKlientError] osv.), legg det til her i libs i stedet for å bygge det lokalt i din egen app.
 * Poenget med modulen er at klientene konvergerer mot samme oppførsel i stedet for at hver app dupliserer sin egen variant.
 *
 * @param clock Klokken som brukes til veggklokke-tidsstempler ([no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata.tidsstempler]). Påkrevd; ingen default i produksjonskode (se AGENTS.md).
 * @param config Klientens oppførsel som ren data. Per-kall-overstyringer finnes ikke; avvikende behov får en egen klientinstans.
 * @param transport Den eneste nettverks-sømmen. Default er produksjonstransporten på `java.net.http.HttpClient`; tester sender inn en `FakeHttpTransport`.
 */
class HttpKlient(
    internal val clock: Clock,
    internal val config: HttpKlientConfig = HttpKlientConfig(),
    internal val transport: HttpTransport = JavaHttpTransport(connectTimeout = config.connectTimeout),
) {
    /** Bygget én gang per klientinstans; jitter-randomness lever i schedulen. */
    internal val retryConfig: RetryConfig = config.retry.toRetryConfig()

    /**
     * Cache av [CircuitBreaker]-instanser per breaker-navn for denne klienten.
     * Entries opprettes lazily og fjernes aldri; én breaker beholdes for klientens levetid per distinkt navn.
     */
    internal val circuitBreakers = ConcurrentHashMap<CircuitBreakerCacheKey, CircuitBreaker>()

    // ---------- JSON inn/ut (det dominerende behovet) ----------

    /** GET → JSON deserialisert til [Res]. Setter `Accept: application/json`. */
    suspend inline fun <reified Res : Any> getJson(
        uri: URI,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> =
        getJsonIntern(typeOf<Res>(), uri, headere, bearerToken, godta)

    /**
     * GET → JSON eller `null` når statusen er i [nullVedStatus].
     * Mentalmodell: [godta] er statusene som skal parses, [nullVedStatus] er statusene som gir `null` uten deserialisering, og suksesskontrakten for kallet er summen av dem.
     * Default er kun `204`: en stille `404 → null`-default ville maskert feilstavede URI-er og feilkonfigurerte gateways som «finnes ikke» — oppslag der `404` faktisk betyr det, sier det eksplisitt med `nullVedStatus = setOf(204, 404)`.
     */
    suspend inline fun <reified Res : Any> getJsonEllerNull(
        uri: URI,
        nullVedStatus: Set<Int> = setOf(204),
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Res?>> =
        getJsonEllerNullIntern(typeOf<Res>(), uri, nullVedStatus, headere, bearerToken, godta)

    /** POST JSON-DTO → JSON. [body] serialiseres med `tiltakspenger-libs/json` ([HttpKlientError.SerializationError] ved feil). */
    suspend inline fun <reified Res : Any> postJson(
        uri: URI,
        body: Any,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> =
        postJsonIntern(typeOf<Res>(), uri, body, headere, bearerToken, godta)

    /** POST ferdigserialisert JSON (sendes verbatim) → JSON. Se [SerialisertJson]. */
    suspend inline fun <reified Res : Any> postJson(
        uri: URI,
        body: SerialisertJson,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> =
        postSerialisertJsonIntern(typeOf<Res>(), uri, body, headere, bearerToken, godta)

    /** Som [postJson], men statuser i [nullVedStatus] regnes som suksess med `null`-body (f.eks. utbetalings simulering der `204` betyr «ingen endring»). */
    suspend inline fun <reified Res : Any> postJsonEllerNull(
        uri: URI,
        body: Any,
        nullVedStatus: Set<Int> = setOf(204),
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Res?>> =
        postJsonEllerNullIntern(typeOf<Res>(), uri, body, nullVedStatus, headere, bearerToken, godta)

    /** Som [postJsonEllerNull], men med ferdigserialisert JSON som sendes verbatim. */
    suspend inline fun <reified Res : Any> postJsonEllerNull(
        uri: URI,
        body: SerialisertJson,
        nullVedStatus: Set<Int> = setOf(204),
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Res?>> =
        postSerialisertJsonEllerNullIntern(typeOf<Res>(), uri, body, nullVedStatus, headere, bearerToken, godta)

    // ---------- JSON inn, kun status ut ----------
    // Bodyen ignoreres typemessig, men fanges alltid lesbart i metadata.rawResponseString.

    /** POST JSON-DTO der responsen ignoreres typemessig (fire-and-forget, dedup-endepunkt o.l.). */
    suspend fun postJsonUtenSvar(
        uri: URI,
        body: Any,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Unit>> =
        utfør(HttpMethod.POST, uri, headere, bearerToken, godta, jsonBody(body), ResponsFormat.IngenBody)

    /** Som [postJsonUtenSvar], men med ferdigserialisert JSON som sendes verbatim. */
    suspend fun postJsonUtenSvar(
        uri: URI,
        body: SerialisertJson,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Unit>> =
        utfør(HttpMethod.POST, uri, headere, bearerToken, godta, HttpKlientRequest.Body.FerdigJson(body.json), ResponsFormat.IngenBody)

    /** PUT JSON-DTO der responsen ignoreres typemessig (dokarkiv oppdater journalpost). */
    suspend fun putJsonUtenSvar(
        uri: URI,
        body: Any,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Unit>> =
        utfør(HttpMethod.PUT, uri, headere, bearerToken, godta, jsonBody(body), ResponsFormat.IngenBody)

    /** PATCH JSON-DTO der responsen ignoreres typemessig (oppgave ferdigstill, dokarkiv ferdigstill). */
    suspend fun patchJsonUtenSvar(
        uri: URI,
        body: Any,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Unit>> =
        utfør(HttpMethod.PATCH, uri, headere, bearerToken, godta, jsonBody(body), ResponsFormat.IngenBody)

    // ---------- PDF / binært (pdfgen, SAF hentdokument) ----------
    // Setter Accept: application/pdf. Responsen dekodes aldri som tekst; metadata får placeholderen «<binær respons, N bytes>» — sikkerlogg-trygt.

    /** POST JSON-DTO → PDF som rå bytes. */
    suspend fun postJsonMotPdf(
        uri: URI,
        body: Any,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<ByteArray>> =
        utfør(HttpMethod.POST, uri, headere, bearerToken, godta, jsonBody(body), ResponsFormat.PdfBytes)

    /** Som [postJsonMotPdf], men med ferdigserialisert JSON som sendes verbatim (pdfgen-payloads som skal persisteres). */
    suspend fun postJsonMotPdf(
        uri: URI,
        body: SerialisertJson,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<ByteArray>> =
        utfør(HttpMethod.POST, uri, headere, bearerToken, godta, HttpKlientRequest.Body.FerdigJson(body.json), ResponsFormat.PdfBytes)

    /** GET → PDF som rå bytes (SAF hentdokument). */
    suspend fun getPdf(
        uri: URI,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<ByteArray>> =
        utfør(HttpMethod.GET, uri, headere, bearerToken, godta, HttpKlientRequest.Body.Ingen, ResponsFormat.PdfBytes)

    // ---------- Rå tekst og form-encoding (tilgangsmaskin, token-endepunkt) ----------

    /**
     * POST rå tekst (`text/plain`) → JSON deserialisert til [Res], eller `Unit` for endepunkt uten interessant body (tilgangsmaskinen svarer `204`).
     * [sensitiv] = `true` maskerer bodyen i `rawRequestString` (tilgangsmaskinen tar fnr som rå tekst).
     */
    suspend inline fun <reified Res : Any> postTekst(
        uri: URI,
        tekst: String,
        sensitiv: Boolean = false,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> =
        postTekstIntern(typeOf<Res>(), uri, tekst, sensitiv, headere, bearerToken, godta)

    /**
     * POST `application/x-www-form-urlencoded` → JSON deserialisert til [Res], eller `Unit`.
     * Nøkler og verdier URL-enkodes (UTF-8), og gjentatte nøkler bevares (`"scope" to "a", "scope" to "b"` gir `scope=a&scope=b`).
     * Praktisk for token-endepunkter (Texas) og andre API-er som forventer form-encoding.
     */
    suspend inline fun <reified Res : Any> postForm(
        uri: URI,
        felter: List<Pair<String, String>>,
        headere: List<Header> = emptyList(),
        bearerToken: AccessToken? = null,
        godta: Statusregel = Statusregel.Alle2xx,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> =
        postFormIntern(typeOf<Res>(), uri, felter, headere, bearerToken, godta)

    // ---------- Interne broer: fanger KType fra de reified fasadene, validerer, og går inn i pipelinen ----------
    // @PublishedApi fordi de kalles fra public inline-metoder; signaturene har kun offentlige typer, slik at de interne modellene (HttpKlientRequest/ResponsFormat) ikke lekker inn i inline-bytecode.

    @PublishedApi
    internal suspend fun <Res> getJsonIntern(
        type: KType,
        uri: URI,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> {
        krevStøttetJsonResponsType(type, metodenavn = "getJson")
        krevIkkeTomBodyStatuser(godta, metodenavn = "getJson", hint = "Bruk getJsonEllerNull(nullVedStatus = ...) eller en UtenSvar-variant.")
        return utfør(HttpMethod.GET, uri, headere, bearerToken, godta, HttpKlientRequest.Body.Ingen, ResponsFormat.Json(type))
    }

    @PublishedApi
    internal suspend fun <Res> getJsonEllerNullIntern(
        type: KType,
        uri: URI,
        nullVedStatus: Set<Int>,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> {
        krevStøttetJsonResponsType(type, metodenavn = "getJsonEllerNull")
        krevIkkeTomBodyStatuser(godta, metodenavn = "getJsonEllerNull", hint = "Legg den i nullVedStatus i stedet.")
        return utfør(HttpMethod.GET, uri, headere, bearerToken, godta, HttpKlientRequest.Body.Ingen, ResponsFormat.JsonEllerNull(type, krevIkkeTomme(nullVedStatus)))
    }

    @PublishedApi
    internal suspend fun <Res> postJsonIntern(
        type: KType,
        uri: URI,
        body: Any,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> {
        krevStøttetJsonResponsType(type, metodenavn = "postJson")
        krevIkkeTomBodyStatuser(godta, metodenavn = "postJson", hint = "Bruk postJsonEllerNull(nullVedStatus = ...) eller en UtenSvar-variant.")
        return utfør(HttpMethod.POST, uri, headere, bearerToken, godta, jsonBody(body), ResponsFormat.Json(type))
    }

    @PublishedApi
    internal suspend fun <Res> postSerialisertJsonIntern(
        type: KType,
        uri: URI,
        body: SerialisertJson,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> {
        krevStøttetJsonResponsType(type, metodenavn = "postJson")
        krevIkkeTomBodyStatuser(godta, metodenavn = "postJson", hint = "Bruk postJsonEllerNull(nullVedStatus = ...) eller en UtenSvar-variant.")
        return utfør(HttpMethod.POST, uri, headere, bearerToken, godta, HttpKlientRequest.Body.FerdigJson(body.json), ResponsFormat.Json(type))
    }

    @PublishedApi
    internal suspend fun <Res> postJsonEllerNullIntern(
        type: KType,
        uri: URI,
        body: Any,
        nullVedStatus: Set<Int>,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> {
        krevStøttetJsonResponsType(type, metodenavn = "postJsonEllerNull")
        krevIkkeTomBodyStatuser(godta, metodenavn = "postJsonEllerNull", hint = "Legg den i nullVedStatus i stedet.")
        return utfør(HttpMethod.POST, uri, headere, bearerToken, godta, jsonBody(body), ResponsFormat.JsonEllerNull(type, krevIkkeTomme(nullVedStatus)))
    }

    @PublishedApi
    internal suspend fun <Res> postSerialisertJsonEllerNullIntern(
        type: KType,
        uri: URI,
        body: SerialisertJson,
        nullVedStatus: Set<Int>,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> {
        krevStøttetJsonResponsType(type, metodenavn = "postJsonEllerNull")
        krevIkkeTomBodyStatuser(godta, metodenavn = "postJsonEllerNull", hint = "Legg den i nullVedStatus i stedet.")
        return utfør(HttpMethod.POST, uri, headere, bearerToken, godta, HttpKlientRequest.Body.FerdigJson(body.json), ResponsFormat.JsonEllerNull(type, krevIkkeTomme(nullVedStatus)))
    }

    @PublishedApi
    internal suspend fun <Res> postTekstIntern(
        type: KType,
        uri: URI,
        tekst: String,
        sensitiv: Boolean,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> =
        utfør(HttpMethod.POST, uri, headere, bearerToken, godta, HttpKlientRequest.Body.Tekst(tekst, sensitiv), jsonEllerUnitResponsFormat(type, metodenavn = "postTekst"))

    @PublishedApi
    internal suspend fun <Res> postFormIntern(
        type: KType,
        uri: URI,
        felter: List<Pair<String, String>>,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> =
        utfør(HttpMethod.POST, uri, headere, bearerToken, godta, HttpKlientRequest.Body.Form(enkodFormFelter(felter)), jsonEllerUnitResponsFormat(type, metodenavn = "postForm"))

    /**
     * Pipeline-inngangen alle metodene delegerer til.
     * Selve pipelinen (auth → bygging → skip-cache-retry → retry → circuit breaker → dekoding) ligger som internal extensions i InternalPipeline.kt.
     */
    private suspend fun <Res> utfør(
        metode: HttpMethod,
        uri: URI,
        headere: List<Header>,
        bearerToken: AccessToken?,
        godta: Statusregel,
        body: HttpKlientRequest.Body,
        responsFormat: ResponsFormat,
    ): Either<HttpKlientError, HttpKlientResponse<Res>> {
        val request = byggHttpKlientRequest(
            metode = metode,
            uri = uri,
            headere = headere,
            bearerToken = bearerToken,
            godta = godta,
            body = body,
            responsFormat = responsFormat,
        )
        return utførBytesRequest(request).flatMap { respons -> respons.tilTypetRespons(responsFormat) }
    }
}

/** Serialiserbar DTO-body, med fail-fast mot de to typene som nesten alltid er en feiltagelse å Jackson-serialisere. */
internal fun jsonBody(body: Any): HttpKlientRequest.Body.Json {
    require(body !is String) {
        "postJson med String-body er tvetydig: bruk SerialisertJson for ferdig JSON, eller postTekst for rå tekst."
    }
    require(body !is ByteArray) {
        "postJson med ByteArray-body støttes ikke — meld behovet i libs hvis et endepunkt trenger rå bytes."
    }
    return HttpKlientRequest.Body.Json(body)
}

/** Fail-fast mot respons-typeargumenter som har en dedikert metode i stedet. */
internal fun krevStøttetJsonResponsType(type: KType, metodenavn: String) {
    when (type.classifier) {
        String::class -> throw IllegalArgumentException(
            "$metodenavn<String> støttes ikke: rå respons-tekst finnes alltid i metadata.rawResponseString, og JSON skal deserialiseres til en DTO.",
        )

        Unit::class -> throw IllegalArgumentException(
            "$metodenavn<Unit> støttes ikke: bruk en UtenSvar-variant når responsen skal ignoreres.",
        )

        ByteArray::class -> throw IllegalArgumentException(
            "$metodenavn<ByteArray> støttes ikke: bruk postJsonMotPdf/getPdf for binære responser.",
        )
    }
}

/**
 * Fail-fast når en statusregel godtar `204`/`205` på en metode som skal deserialisere bodyen.
 * RFC 9110 garanterer at disse statusene er uten body, så deserialisering ville alltid feilet ved runtime — [hint] peker til riktig alternativ for metoden.
 */
internal fun krevIkkeTomBodyStatuser(godta: Statusregel, metodenavn: String, hint: String) {
    if (godta is Statusregel.Eksakt) {
        val tomBodyStatuser = godta.statuser.filter { it == 204 || it == 205 }
        require(tomBodyStatuser.isEmpty()) {
            "$metodenavn kan ikke godta status ${tomBodyStatuser.joinToString()} — den har per RFC 9110 ingen body. $hint"
        }
    }
}

/** `Unit` betyr «ignorer bodyen» for tekst-/form-metodene (som ikke har egne UtenSvar-varianter); alt annet deserialiseres fra JSON. */
internal fun jsonEllerUnitResponsFormat(type: KType, metodenavn: String): ResponsFormat = when (type.classifier) {
    Unit::class -> ResponsFormat.IngenBody

    String::class -> throw IllegalArgumentException(
        "$metodenavn<String> støttes ikke: rå respons-tekst finnes alltid i metadata.rawResponseString.",
    )

    ByteArray::class -> throw IllegalArgumentException(
        "$metodenavn<ByteArray> støttes ikke: bruk postJsonMotPdf/getPdf for binære responser.",
    )

    else -> ResponsFormat.Json(type)
}

internal fun krevIkkeTomme(nullVedStatus: Set<Int>): Set<Int> {
    require(nullVedStatus.isNotEmpty()) { "nullVedStatus kan ikke være tom — bruk metoden uten EllerNull i stedet." }
    return nullVedStatus
}

/** URL-enkoder feltene til én `application/x-www-form-urlencoded`-body; gjentatte nøkler bevares. */
internal fun enkodFormFelter(felter: List<Pair<String, String>>): String =
    felter.joinToString("&") { (navn, verdi) ->
        "${URLEncoder.encode(navn, StandardCharsets.UTF_8)}=${URLEncoder.encode(verdi, StandardCharsets.UTF_8)}"
    }

/** Materialiserer metode-parametrene til den interne request-modellen, med `Content-Type`/`Accept` utledet av body og responsformat. */
internal fun byggHttpKlientRequest(
    metode: HttpMethod,
    uri: URI,
    headere: List<Header>,
    bearerToken: AccessToken?,
    godta: Statusregel,
    body: HttpKlientRequest.Body,
    responsFormat: ResponsFormat,
): HttpKlientRequest {
    // Innsettingsordnet og case-insensitivt: gjentatt headernavn gir multi-verdi-header med første casing bevart.
    val konsumentHeadere = LinkedHashMap<String, MutableList<String>>()
    headere.forEach { header ->
        val eksisterendeNøkkel = konsumentHeadere.keys.firstOrNull { it.equals(header.navn, ignoreCase = true) }
        if (eksisterendeNøkkel != null) {
            konsumentHeadere.getValue(eksisterendeNøkkel).add(header.verdi)
        } else {
            konsumentHeadere[header.navn] = mutableListOf(header.verdi)
        }
    }
    val contentType = when (body) {
        HttpKlientRequest.Body.Ingen -> null
        is HttpKlientRequest.Body.Json, is HttpKlientRequest.Body.FerdigJson -> "application/json"
        is HttpKlientRequest.Body.Tekst -> "text/plain; charset=utf-8"
        is HttpKlientRequest.Body.Form -> "application/x-www-form-urlencoded"
    }
    val accept = when (responsFormat) {
        is ResponsFormat.Json, is ResponsFormat.JsonEllerNull -> "application/json"
        ResponsFormat.PdfBytes -> "application/pdf"
        ResponsFormat.IngenBody -> null
    }
    // Reserverte navn avvises i Header.init, så klientens defaults kan aldri kollidere med konsumentens headere.
    val alleHeadere = buildMap(konsumentHeadere.size + 2) {
        konsumentHeadere.forEach { (navn, verdier) -> put(navn, verdier.toList()) }
        contentType?.let { put("Content-Type", listOf(it)) }
        accept?.let { put("Accept", listOf(it)) }
    }
    return HttpKlientRequest(
        method = metode,
        uri = uri,
        headers = alleHeadere,
        sensitiveHeaderNavn = headere.filter { it.sensitiv }.map { it.navn.lowercase() }.toSet(),
        body = body,
        authToken = bearerToken,
        godta = godta,
        responsFormat = responsFormat,
    )
}
