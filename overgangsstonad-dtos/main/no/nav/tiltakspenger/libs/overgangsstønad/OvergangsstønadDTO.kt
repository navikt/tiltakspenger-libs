package no.nav.tiltakspenger.libs.overgangsstønad

import java.time.LocalDate

data class OvergangsstønadDTO(
    val perioder: List<OvergangsstønadPeriodeDTO>,
    val datoUfor: LocalDate?,
    val virkDato: LocalDate?,
)
