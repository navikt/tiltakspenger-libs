package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.common.AccessToken
import java.net.URI
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
    private val headers = linkedMapOf<String, List<String>>()
    private var requestBody: RequestBody? = null

    /**
     * Setter [name] til [value] og fjerner eventuelle eksisterende verdier for samme nøkkel
     * (samme semantikk som OkHttp `Request.Builder.header`). Bruk [addHeader] for å sende flere
     * verdier på samme nøkkel.
     */
    fun header(name: String, value: String) {
        headers[name] = listOf(value)
    }

    /**
     * Legger til en ekstra verdi for [name] uten å fjerne tidligere verdier (samme semantikk som
     * OkHttp `Request.Builder.addHeader`). Brukes til multi-value-headere som `Accept-Encoding`,
     * `Cookie`, `Set-Cookie`, etc.
     */
    fun addHeader(name: String, value: String) {
        headers[name] = (headers[name] ?: emptyList()) + value
    }

    fun acceptJson() {
        header("Accept", "application/json")
    }

    fun contentTypeJson() {
        header("Content-Type", "application/json")
    }

    fun successStatus(predicate: (Int) -> Boolean) {
        successStatusOverride = predicate
    }

    fun logging(build: HttpKlientLoggingConfigBuilder.() -> Unit) {
        loggingConfigOverride = HttpKlientLoggingConfig.build(build)
    }

    fun disableLogging() {
        loggingConfigOverride = HttpKlientLoggingConfig.Disabled
    }

    /**
     * Setter et bearer-token som vil bli sendt som `Authorization: Bearer <token>` på denne
     * requesten. Overstyrer eventuell `authTokenProvider` på [HttpKlient]. Konsumenter som
     * allerede setter `Authorization` eksplisitt via [header]/[addHeader] vil få sin verdi
     * beholdt uendret.
     */
    fun bearerToken(accessToken: AccessToken) {
        authToken = accessToken
    }

    /**
     * Sender bodyen som rå tekst uten å legge til JSON-headere automatisk.
     * Bruk denne når payloaden ikke er JSON, eller når alle headere settes eksplisitt av konsumenten.
     */
    fun body(body: String) {
        requestBody = RequestBody.Raw(body)
    }

    /**
     * Sender en ferdigserialisert JSON-string og legger til `Content-Type: application/json` hvis
     * den ikke allerede er satt case-insensitivt.
     *
     * `Accept`-headeren styres bevisst _ikke_ herfra — den følger forventet response-type på
     * `request<T>(...)`/verb-extensionene, som setter `Accept: application/json` automatisk for
     * alle response-typer som ikke er `String` eller `Unit`.
     */
    fun json(body: String) {
        requestBody = RequestBody.RawJson(body)
    }

    /**
     * Serialiserer DTO-en med `tiltakspenger-libs/json` og legger til `Content-Type: application/json`
     * hvis den ikke allerede er satt case-insensitivt.
     *
     * `Accept`-headeren styres bevisst _ikke_ herfra — den følger forventet response-type på
     * `request<T>(...)`/verb-extensionene, som setter `Accept: application/json` automatisk for
     * alle response-typer som ikke er `String` eller `Unit`.
     */
    fun json(value: Any) {
        requestBody = RequestBody.Json(value)
    }

    /**
     * Lager en public projeksjon av requesten etter at klientens default-headere for [responseType]
     * og body-type er lagt til.
     *
     * Eksisterer **kun** for å la `HttpKlientFake` (i `test-common`) ta vare på requests som ble
     * gjort, slik at tester kan asserte på dem. Skal **ikke** brukes fra produksjonskode — bruk
     * [HttpKlient.unreifiedRequest] / verb-extensionene istedenfor. Den interne
     * [BuiltHttpKlientRequest] (med retry/CB/logging-config) eksponeres bevisst ikke; fakes
     * trenger bare [HttpKlientRequest] sine felt for å lage meningsfulle assertions.
     */
    fun snapshot(responseType: KType, method: HttpMethod): HttpKlientRequest {
        return build(method)
            .withDefaultJsonAcceptHeaderForResponse(responseType)
            .let { request ->
                request.toHttpKlientRequest(headers = request.effectiveRequestHeaders())
            }
    }

    internal fun build(method: HttpMethod): BuiltHttpKlientRequest {
        return BuiltHttpKlientRequest(
            uri = uri,
            method = method,
            headers = headers.toMap(),
            body = requestBody,
            timeout = timeout,
            successStatus = successStatusOverride,
            loggingConfig = loggingConfigOverride,
            retryConfig = retryConfig,
            circuitBreakerConfig = circuitBreakerConfig,
            authToken = authToken,
        )
    }
}

private fun BuiltHttpKlientRequest.toHttpKlientRequest(
    headers: Map<String, List<String>>,
): HttpKlientRequest {
    return HttpKlientRequest(
        uri = uri,
        method = method,
        headers = headers,
        body = body.toHttpKlientRequestBody(),
        timeout = timeout,
        authToken = authToken,
    )
}

private fun RequestBody?.toHttpKlientRequestBody(): HttpKlientRequest.Body? = when (this) {
    is RequestBody.Json -> HttpKlientRequest.Body.Json(value)
    is RequestBody.Raw -> HttpKlientRequest.Body.Raw(body)
    is RequestBody.RawJson -> HttpKlientRequest.Body.RawJson(body)
    null -> null
}
