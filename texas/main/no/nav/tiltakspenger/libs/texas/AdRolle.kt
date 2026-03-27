package no.nav.tiltakspenger.libs.texas

import no.nav.tiltakspenger.libs.common.Saksbehandlerrolle

/**
 * @param objectId Id til rollen i Entra Id (Azure AD). Typisk en UUID.
 */
data class AdRolle(
    val name: Saksbehandlerrolle,
    val objectId: String,
)
