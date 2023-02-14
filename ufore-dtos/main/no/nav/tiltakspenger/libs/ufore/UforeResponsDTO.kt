package no.nav.tiltakspenger.libs.ufore

data class UforeResponsDTO(
    val uføregrad: UføregradDTO? = null,
    val feil: Feilmelding? = null,
)
