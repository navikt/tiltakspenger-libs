package no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse

import no.nav.tiltakspenger.libs.personklient.pdl.dto.AdressebeskyttelseGradering

internal data class PipPersondataResponse(
    val person: PipPerson,
) {
    data class PipPerson(
        val adressebeskyttelse: List<Adressebeskyttelse>,
    ) {
        data class Adressebeskyttelse(
            val gradering: AdressebeskyttelseGradering,
        )
    }

    fun toPersonDtoGradering(): List<no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering> =
        person.adressebeskyttelse.map {
            it.gradering.toPersonDto(it.gradering)
        }
}
