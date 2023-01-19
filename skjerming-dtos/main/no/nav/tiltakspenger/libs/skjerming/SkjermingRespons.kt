package no.nav.tiltakspenger.libs.skjerming

data class SkjermingResponsDTO(
    val søker : SkjermingPersonDTO? = null,
    val barn : List<SkjermingPersonDTO>? = null,
    val feil: Feilmelding? = null
)
