package no.nav.tiltakspenger.libs.texas

import no.nav.tiltakspenger.libs.common.Fnr

data class TexasPrincipalExternalUser(
    val claims: Map<String, Any?>,
    val token: String,
    val fnr: Fnr,
)
