package no.nav.tiltakspenger.libs.skjerming

data class SkjermingResponsDTO(
    val skjermingForPersoner: SkjermingDTO? = null,
    val feil: Feilmelding? = null
)
