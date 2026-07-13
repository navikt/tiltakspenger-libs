package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.reflect.KType
import kotlin.time.Duration

class RequestBuilder(
    val uri: URI,
) {
    var timeout: Duration? = null
    var retryConfig: RetryConfig? = null
    var circuitBreakerConfig: CircuitBreakerConfig? = null
    var authToken: AccessToken? = null

    private var successStatusOverride: ((Int) -> Boolean)? = null
    private var loggingConfigOverride: HttpKlientLoggingConfig? = null

    // Innsettingsordnet map fordi HttpKlientRequest-kontrakten lover at headere bevarer rekkefølgen konsumenten satte dem i, noe som gir forutsigbare logger/rawRequestString.
    // Case-insensitivitet (RFC 9110 §5.1) håndteres i setHeader, som matcher eksisterende nøkkel uavhengig av casing i stedet for å sortere nøklene.
    // Holdes som en uforanderlig Map som reassignes funksjonelt (jf. de øvrige builder-feltene) i stedet for å muteres på plass.
    private var headers: Map<String, List<String>> = emptyMap()
    private var requestBody: HttpKlientRequest.Body? = null

    /**
     * Setter [name] til [value] og fjerner eventuelle eksisterende verdier for samme nøkkel (samme semantikk som OkHttp `Request.Builder.header`).
     * Bruk [addHeader] for å sende flere verdier på samme nøkkel.
     *
     * HTTP-headernavn er case-insensitive (RFC 9110 §5.1), så en eksisterende header med annen casing (f.eks. `X-Foo` vs `x-foo`) overskrives i stedet for å gi to separate headere i requesten.
     */
    fun header(name: String, value: String) {
        setHeader(name, listOf(value))
    }

    /**
     * Legger til en ekstra verdi for [name] uten å fjerne tidligere verdier (samme semantikk som OkHttp `Request.Builder.addHeader`).
     * Brukes til multi-value-headere som `Accept-Encoding`, `Cookie`, `Set-Cookie`, etc.
     *
     * HTTP-headernavn er case-insensitive (RFC 9110 §5.1), så verdien legges til på en eventuell eksisterende header med annen casing (f.eks. `X-Foo` vs `x-foo`) i stedet for å opprette en duplikat-header.
     */
    fun addHeader(name: String, value: String) {
        val eksisterendeVerdier = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: emptyList()
        setHeader(name, eksisterendeVerdier + value)
    }

    /**
     * Setter [name] til [values] og fjerner en eventuell eksisterende header med samme navn men annen casing (RFC 9110 §5.1).
     * Casingen oppdateres alltid til den siste skriveren ([name]), slik at den nyeste [header]/[addHeader]-callen bestemmer hvordan navnet vises i logger/`rawRequestString`.
     * Headerens opprinnelige posisjon i innsettingsrekkefølgen bevares når bare casingen endres.
     */
    private fun setHeader(name: String, values: List<String>) {
        val eksisterendeNøkkel = headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }
        headers = when (eksisterendeNøkkel) {
            // Ny header, eller samme casing som før: Map.plus legger til på slutten eller oppdaterer verdien på plass uten å endre rekkefølgen.
            null, name -> headers + (name to values)

            // Casingen er endret: map over entryene og bytt ut nøkkelen på samme posisjon, slik at den siste casingen vinner uten at headeren flyttes bakerst.
            else -> headers.entries.associate { (nøkkel, gamleVerdier) ->
                if (nøkkel == eksisterendeNøkkel) name to values else nøkkel to gamleVerdier
            }
        }
    }

    fun acceptJson() {
        header("Accept", "application/json")
    }

    fun contentTypeJson() {
        header("Content-Type", "application/json")
    }

    /** Overstyrer hvilke statuser som regnes som suksess for denne requesten. */
    fun successStatus(predicate: (Int) -> Boolean) {
        successStatusOverride = predicate
    }

    /** Som [successStatus], men godtar kun de eksplisitt oppgitte statuskodene (f.eks. `successStatus(200, 201)`). */
    fun successStatus(vararg codes: Int) {
        successStatus(HttpStatusSuccess.exactly(*codes))
    }

    /** Som [successStatus], men godtar alle statuskoder i [range] (f.eks. `successStatus(200..204)`). */
    fun successStatus(range: IntRange) {
        successStatus(HttpStatusSuccess.inRange(range))
    }

    fun logging(build: HttpKlientLoggingConfigBuilder.() -> Unit) {
        loggingConfigOverride = HttpKlientLoggingConfig.build(build)
    }

    fun disableLogging() {
        loggingConfigOverride = HttpKlientLoggingConfig.Disabled
    }

    /**
     * Setter et bearer-token som vil bli sendt som `Authorization: Bearer <token>` på denne requesten.
     * Overstyrer eventuell `authTokenProvider` på [HttpKlient].
     * Konsumenter som allerede setter `Authorization` eksplisitt via [header]/[addHeader] vil få sin verdi beholdt uendret.
     */
    fun bearerToken(accessToken: AccessToken) {
        authToken = accessToken
    }

    /**
     * Sender bodyen som rå tekst uten å legge til JSON-headere automatisk.
     * Bruk denne når payloaden ikke er JSON, eller når alle headere settes eksplisitt av konsumenten.
     */
    fun body(body: String) {
        requestBody = HttpKlientRequest.Body.Raw(body)
    }

    /**
     * Sender [fields] som `application/x-www-form-urlencoded` body.
     * Nøkler og verdier URL-kodes (UTF-8), og `Content-Type: application/x-www-form-urlencoded` settes hvis den ikke allerede er satt case-insensitivt.
     * Gjentatte nøkler bevares (f.eks. `formUrlEncoded("scope" to "a", "scope" to "b")` gir `scope=a&scope=b`), siden gjentatte felter er gyldige i form-encoding.
     * Praktisk for token-endepunkter og andre legacy-API-er som forventer form-encoding i stedet for JSON.
     */
    fun formUrlEncoded(vararg fields: Pair<String, String>) {
        formUrlEncodedFields(fields.asList())
    }

    /**
     * Som [formUrlEncoded] med varargs, men tar et ferdig [felter]-Map.
     * Merk at et `Map` ikke kan ha duplikate nøkler; bruk varargs-formen hvis du trenger gjentatte felter.
     */
    fun formUrlEncoded(felter: Map<String, String>) {
        formUrlEncodedFields(felter.map { (name, value) -> name to value })
    }

    private fun formUrlEncodedFields(fields: List<Pair<String, String>>) {
        val encoded = fields.joinToString("&") { (name, value) ->
            "${URLEncoder.encode(name, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
        requestBody = HttpKlientRequest.Body.Raw(encoded)
        if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
            header("Content-Type", "application/x-www-form-urlencoded")
        }
    }

    /**
     * Sender en ferdigserialisert JSON-string og legger til `Content-Type: application/json` hvis den ikke allerede er satt case-insensitivt.
     *
     * `Accept`-headeren styres bevisst _ikke_ herfra — den følger forventet response-type på `request<T>(...)`/verb-extensionene, som setter `Accept: application/json` automatisk for alle response-typer som ikke er `String`, `Unit` eller `ByteArray`.
     */
    fun json(body: String) {
        requestBody = HttpKlientRequest.Body.RawJson(body)
    }

    /**
     * Serialiserer DTO-en med `tiltakspenger-libs/json` og legger til `Content-Type: application/json` hvis den ikke allerede er satt case-insensitivt.
     *
     * `Accept`-headeren styres bevisst _ikke_ herfra — den følger forventet response-type på `request<T>(...)`/verb-extensionene, som setter `Accept: application/json` automatisk for alle response-typer som ikke er `String`, `Unit` eller `ByteArray`.
     */
    fun json(value: Any) {
        requestBody = HttpKlientRequest.Body.Json(value)
    }

    /**
     * Materialiserer builderen til en [HttpKlientRequest] som [HttpKlient.request] kan ta imot.
     * Legger på klientens default-headere: `Accept: application/json` for [responseType] som ikke er `String`/`Unit`/`ByteArray`, og `Content-Type: application/json` for JSON-body — begge kun hvis de mangler (case-insensitivt).
     *
     * `@PublishedApi internal` fordi den kalles fra de public reified verb-extensionene (som er `inline`), men ikke skal være en del av det public API-et.
     */
    @PublishedApi
    internal fun materialize(responseType: KType, method: HttpMethod): HttpKlientRequest {
        val headersMedAccept = when (responseType.classifier) {
            // For String/Unit/ByteArray vet vi ikke hvilket format konsumenten forventer (tekst, ingenting, binært — f.eks. `application/pdf`), så konsumenten setter selv Accept ved behov.
            String::class, Unit::class, ByteArray::class -> headers.toMap()

            else -> headers.withDefaultJsonAcceptHeader()
        }
        val headersMedContentType = when (requestBody) {
            is HttpKlientRequest.Body.Json, is HttpKlientRequest.Body.RawJson -> headersMedAccept.withDefaultJsonContentTypeHeader()
            else -> headersMedAccept
        }
        return HttpKlientRequest(
            uri = uri,
            method = method,
            headers = headersMedContentType,
            body = requestBody,
            timeout = timeout,
            authToken = authToken,
            successStatus = successStatusOverride,
            loggingConfig = loggingConfigOverride,
            retryConfig = retryConfig,
            circuitBreakerConfig = circuitBreakerConfig,
        )
    }
}
