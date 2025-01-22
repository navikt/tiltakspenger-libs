package no.nav.tiltakspenger.libs.periodisering

import java.time.LocalDate

/** Skal kun brukes i infrastrukturlaget (serialisering/deserialisering). Bruk [Periode] i domenet. */
data class PeriodeDTO(
    val fraOgMed: String,
    val tilOgMed: String,
) {
    fun toDomain(): Periode = Periode(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
}

fun Periode.toDTO(): PeriodeDTO = PeriodeDTO(fraOgMed.toString(), tilOgMed.toString())
