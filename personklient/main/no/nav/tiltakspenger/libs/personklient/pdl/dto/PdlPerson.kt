package no.nav.tiltakspenger.libs.personklient.pdl.dto

internal data class PdlPerson(
    val navn: List<Navn>,
    val foedsel: List<Fødsel>,
    val adressebeskyttelse: List<Adressebeskyttelse>,
    val forelderBarnRelasjon: List<ForelderBarnRelasjon>,
)
