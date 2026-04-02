package no.nav.tiltakspenger.libs.periode

import java.time.LocalDate

/**
 * Skal kun brukes i infrastrukturlaget (serialisering/deserialisering). Bruk [Periode] i domenet.
 * Merk: Denne bor i periodisering-modulen for å være tilgjengelig for alle konsumenter,
 * men hører logisk hjemme i et infrastruktur-/DTO-lag.
 */
data class PeriodeDTO(
    val fraOgMed: String,
    val tilOgMed: String,
) {
    fun toDomain(): Periode = Periode(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
}

fun Periode.toDTO(): PeriodeDTO = PeriodeDTO(fraOgMed.toString(), tilOgMed.toString())
