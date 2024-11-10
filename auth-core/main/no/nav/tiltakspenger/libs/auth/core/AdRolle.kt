package no.nav.tiltakspenger.libs.auth.core

import no.nav.tiltakspenger.libs.common.Saksbehandlerrolle

/**
 * @param objectId Id til rollen i Entra Id (Azure AD). Typisk en UUID.
 */
data class AdRolle(
    val name: Saksbehandlerrolle,
    val objectId: String,
)
