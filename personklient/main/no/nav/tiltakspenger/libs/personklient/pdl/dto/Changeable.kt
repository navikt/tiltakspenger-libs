package no.nav.tiltakspenger.libs.personklient.pdl.dto

import java.time.LocalDateTime

internal interface Changeable {
    val metadata: EndringsMetadata
    val folkeregistermetadata: FolkeregisterMetadata?
}

internal fun getEndringstidspunktOrNull(data: Changeable): LocalDateTime? =
    when {
        data.metadata.master.isFreg() -> data.folkeregistermetadata?.ajourholdstidspunkt
        else -> data.metadata.endringer.nyeste()?.registrert
    }
