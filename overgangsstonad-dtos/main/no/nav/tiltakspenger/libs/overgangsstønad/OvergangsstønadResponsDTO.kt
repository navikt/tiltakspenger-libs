package no.nav.tiltakspenger.libs.overgangsstønad

data class OvergangsstønadResponsDTO(
    val overgangsstønad: OvergangsstønadDTO? = null,
    val feil: Feilmelding? = null,
)
