package no.nav.tiltakspenger.libs.personklient.pdl.dto

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError
import no.nav.tiltakspenger.libs.personklient.pdl.dto.AdressebeskyttelseGradering.UGRADERT

enum class AdressebeskyttelseGradering {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT,
    ;

    internal fun toPersonDto(adressebeskyttelse: AdressebeskyttelseGradering) =
        when (adressebeskyttelse) {
            STRENGT_FORTROLIG_UTLAND -> no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
            STRENGT_FORTROLIG -> no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.STRENGT_FORTROLIG
            FORTROLIG -> no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.FORTROLIG
            UGRADERT -> no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.UGRADERT
        }

    fun erFortrolig() = this == FORTROLIG
    fun erStrengtFortrolig() = this == STRENGT_FORTROLIG
    fun erStrengtFortroligUtland() = this == STRENGT_FORTROLIG_UTLAND
}

data class Adressebeskyttelse(
    val gradering: AdressebeskyttelseGradering,
    override val folkeregistermetadata: FolkeregisterMetadata? = null,
    override val metadata: EndringsMetadata,
) : Changeable

fun avklarGradering(gradering: List<Adressebeskyttelse>): Either<FellesPersonklientError, AdressebeskyttelseGradering> {
    return if (gradering.isEmpty()) {
        UGRADERT.right()
    } else {
        gradering
            .sortedByDescending { getEndringstidspunktOrNull(it) }
            .firstOrNull { !kildeErUdokumentert(it.metadata) }?.gradering?.right()
            ?: FellesPersonklientError.AdressebeskyttelseKunneIkkeAvklares.left()
    }
}
