package no.nav.tiltakspenger.libs.periode

import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

fun Int.uke(år: Int): Periode {
    val weekFields = WeekFields.of(Locale.of("no", "NO"))

    // Finn første dag i uken (mandag)
    val førsteDagIUken = LocalDate.of(år, 1, 1)
        .with(weekFields.weekOfYear(), this.toLong())
        .with(weekFields.dayOfWeek(), 1)

    // Siste dag i uken er søndag (6 dager etter mandag)
    val sisteDagIUken = førsteDagIUken.plusDays(6)

    return Periode(førsteDagIUken, sisteDagIUken)
}
