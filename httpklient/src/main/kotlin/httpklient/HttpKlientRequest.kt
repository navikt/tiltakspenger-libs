package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.common.AccessToken
import java.net.URI
import kotlin.time.Duration

/**
 * Public, **test-only** projeksjon av en request slik den er bygget med [RequestBuilder].
 *
 * Eksisterer kun for at `HttpKlientFake` (i `test-common`) skal kunne eksponere innkommende
 * requests for assertion fra konsumentenes tester. Skal **ikke** brukes fra produksjonskode —
 * der jobber man kun mot [HttpKlient]-interfacet.
 *
 * Bevisst trimmet projeksjon: vi eksponerer kun feltene en test kan asserte meningsfullt på
 * (uri, method, headers, body, timeout, authToken). Per-request retry-, circuit-breaker-,
 * logging- og success-status-overrides holdes utenfor, så testkode ikke binder seg til
 * implementasjonsdetaljer i `httpklient`.
 *
 * Headere bevarer innsettingsrekkefølge, med eventuelle default-headere som klienten legger til,
 * til slutt.
 */
data class HttpKlientRequest(
    val uri: URI,
    val method: HttpMethod,
    val headers: Map<String, List<String>>,
    val body: Body?,
    val timeout: Duration?,
    val authToken: AccessToken?,
) {
    sealed interface Body {
        data class Raw(val body: String) : Body
        data class RawJson(val body: String) : Body
        data class Json(val value: Any) : Body
    }
}
