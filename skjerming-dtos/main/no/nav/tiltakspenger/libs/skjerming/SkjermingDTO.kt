package no.nav.tiltakspenger.libs.skjerming

data class SkjermingDTO(
    val søker : SkjermingPersonDTO,
    val barn : List<SkjermingPersonDTO>? = null,
)
