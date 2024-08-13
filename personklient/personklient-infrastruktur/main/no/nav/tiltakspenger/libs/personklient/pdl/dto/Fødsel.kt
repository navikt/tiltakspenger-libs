package no.nav.tiltakspenger.libs.personklient.pdl.dto

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError
import java.time.LocalDate

data class Fødsel(
    val foedselsdato: LocalDate,
    override val folkeregistermetadata: FolkeregisterMetadata,
    override val metadata: EndringsMetadata,
) : Changeable

internal const val FREG = "FREG"
internal fun String.isFreg() = this.equals(FREG, ignoreCase = true)

fun avklarFødsel(foedsler: List<Fødsel>): Either<FellesPersonklientError, Fødsel> {
    val foedslerSortert = foedsler.sortedByDescending { getEndringstidspunktOrNull(it) }
    val foedselFreg = foedslerSortert.find { it.metadata.master.isFreg() }
    return foedselFreg?.right() ?: foedslerSortert.firstOrNull()?.right()
        ?: FellesPersonklientError.FødselKunneIkkeAvklares.left()
}
