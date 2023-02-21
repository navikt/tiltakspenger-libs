package no.nav.tiltakspenger.libs.overgangsstonad

data class OvergangsstønadResponsDTO(
    val overgangsstønader: List<OvergangsstønadPeriodeDTO>? = null,
    val feil: Feilmelding? = null,
)
