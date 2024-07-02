package no.nav.tiltakspenger.libs.personklient.pdl.dto

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.personklient.pdl.PDLClientError
import java.time.LocalDate

internal data class Fødsel(
    val foedselsdato: LocalDate,
    override val folkeregistermetadata: FolkeregisterMetadata,
    override val metadata: EndringsMetadata,
) : Changeable

internal const val FREG = "FREG"
internal fun String.isFreg() = this.equals(FREG, ignoreCase = true)

internal fun avklarFødsel(foedsler: List<Fødsel>): Either<PDLClientError, Fødsel> {
    val foedslerSortert = foedsler.sortedByDescending { getEndringstidspunktOrNull(it) }
    val foedselFreg = foedslerSortert.find { it.metadata.master.isFreg() }
    return foedselFreg?.right() ?: foedslerSortert.firstOrNull()?.right()
        ?: PDLClientError.FødselKunneIkkeAvklares.left()
}
