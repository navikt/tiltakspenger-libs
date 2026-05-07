package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.common.AccessToken
import java.net.URI
import java.net.http.HttpRequest
import kotlin.time.Duration

internal data class BuiltHttpKlientRequest(
    val uri: URI,
    val method: HttpMethod,
    /**
     * Headere fra `RequestBuilder`. Bevarer innsettings-rekkefølge (kommer fra `linkedMapOf` /
     * `buildMap`). Default-headere som klienten legger til (f.eks. JSON `Content-Type` for body
     * og `Accept` for response-type) havner alltid til slutt.
     */
    val headers: Map<String, List<String>>,
    val body: RequestBody?,
    val timeout: Duration?,
    val successStatus: ((Int) -> Boolean)?,
    val loggingConfig: HttpKlientLoggingConfig?,
    val retryConfig: RetryConfig?,
    val circuitBreakerConfig: CircuitBreakerConfig?,
    /**
     * Per-request bearer-token. Overstyrer `HttpKlient.HttpKlientConfig.authTokenProvider`. Brukes
     * til å sende `Authorization: Bearer <token>` for denne requesten.
     */
    val authToken: AccessToken?,
)

internal data class PreparedHttpKlientRequest(
    val request: HttpRequest,
    val rawRequestString: String,
)

internal sealed interface RequestBody {
    /**
     * Rå tekst-body. `httpklient` legger ikke til JSON-headere for denne varianten.
     */
    data class Raw(val body: String) : RequestBody

    /**
     * Ferdigserialisert JSON-string. `httpklient` legger til standard JSON-headere hvis de mangler.
     */
    data class RawJson(val body: String) : RequestBody

    /**
     * DTO/verdi som serialiseres med `tiltakspenger-libs/json` før requesten sendes.
     */
    data class Json(val value: Any) : RequestBody
}
