package no.nav.tiltakspenger.libs.personklient.pdl.dto

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError
import no.nav.tiltakspenger.libs.personklient.pdl.dto.AdressebeskyttelseGradering.UGRADERT

internal enum class AdressebeskyttelseGradering {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT,
}

internal data class Adressebeskyttelse(
    val gradering: AdressebeskyttelseGradering,
    override val folkeregistermetadata: FolkeregisterMetadata? = null,
    override val metadata: EndringsMetadata,
) : Changeable

internal fun avklarGradering(gradering: List<Adressebeskyttelse>): Either<FellesPersonklientError, AdressebeskyttelseGradering> {
    return if (gradering.isEmpty()) {
        UGRADERT.right()
    } else {
        gradering
            .sortedByDescending { getEndringstidspunktOrNull(it) }
            .firstOrNull { !kildeErUdokumentert(it.metadata) }?.gradering?.right()
            ?: FellesPersonklientError.AdressebeskyttelseKunneIkkeAvklares.left()
    }
}
