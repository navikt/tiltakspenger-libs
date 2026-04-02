package no.nav.tiltakspenger.libs.personklient.pdl.dto

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError

data class Navn(
    val fornavn: String,
    val etternavn: String,
    val mellomnavn: String? = null,
    override val metadata: EndringsMetadata,
    override val folkeregistermetadata: FolkeregisterMetadata? = null,
) : Changeable

fun avklarNavn(navn: List<Navn>): Either<FellesPersonklientError, Navn> {
    if (navn.isEmpty()) return FellesPersonklientError.IngenNavnFunnet.left()
    return navn
        .sortedByDescending { getEndringstidspunktOrNull(it) }
        .firstOrNull { !kildeErUdokumentert(it.metadata) }?.right()
        ?: FellesPersonklientError.NavnKunneIkkeAvklares.left()
}

fun kildeErUdokumentert(metadata: EndringsMetadata) =
    metadata.master == Kilde.PDL && metadata.endringer.nyeste()?.kilde == Kilde.BRUKER_SELV
