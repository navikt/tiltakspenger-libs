package no.nav.tiltakspenger.libs.personklient.pdl.dto

data class PdlPerson(
    val navn: List<Navn> = emptyList(),
    val foedselsdato: List<Fødsel> = emptyList(),
    val adressebeskyttelse: List<Adressebeskyttelse> = emptyList(),
    val forelderBarnRelasjon: List<ForelderBarnRelasjon> = emptyList(),
)
