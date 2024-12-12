package no.nav.tiltakspenger.libs.periodisering

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

fun LocalDate.tilstøter(annen: LocalDate): Boolean {
    return abs(this.until(annen, ChronoUnit.DAYS)) == 1L
}

operator fun LocalDate.rangeTo(that: LocalDate): Periode {
    return Periode(this, that)
}
