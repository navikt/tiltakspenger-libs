package no.nav.tiltakspenger.libs.overgangsstonad

data class OvergangsstønadResponsDTO(
    val overgangsstønad: OvergangsstønadDTO? = null,
    val feil: Feilmelding? = null,
)
