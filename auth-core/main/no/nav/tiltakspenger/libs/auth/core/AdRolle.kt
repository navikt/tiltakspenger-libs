package no.nav.tiltakspenger.libs.auth.core

import no.nav.tiltakspenger.libs.common.Rolle

/**
 * @param objectId Id til rollen i Entra Id (Azure AD). Typisk en UUID.
 */
data class AdRolle(
    val name: Rolle,
    val objectId: String,
)
