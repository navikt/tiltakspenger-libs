package no.nav.tiltakspenger.libs.texas

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import no.nav.tiltakspenger.libs.common.Fnr

data class TexasPrincipalExternalUser(
    val claims: Map<String, Any?>,
    val token: String,
    val fnr: Fnr,
)

fun ApplicationCall.fnr(): Fnr {
    val principal = principal<TexasPrincipalExternalUser>() ?: throw IllegalStateException("Mangler principal")
    return principal.fnr
}
