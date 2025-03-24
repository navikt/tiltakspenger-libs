package no.nav.tiltakspenger.libs.periodisering

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.temporal.ChronoUnit

internal infix fun Int.januar(year: Int): LocalDate = LocalDate.of(year, Month.JANUARY, this)
internal infix fun Int.februar(year: Int): LocalDate = LocalDate.of(year, Month.FEBRUARY, this)
internal infix fun Int.mars(year: Int): LocalDate = LocalDate.of(year, Month.MARCH, this)
internal infix fun Int.april(year: Int): LocalDate = LocalDate.of(year, Month.APRIL, this)
internal infix fun Int.mai(year: Int): LocalDate = LocalDate.of(year, Month.MAY, this)
internal infix fun Int.juni(year: Int): LocalDate = LocalDate.of(year, Month.JUNE, this)
internal infix fun Int.juli(year: Int): LocalDate = LocalDate.of(year, Month.JULY, this)
internal infix fun Int.august(year: Int): LocalDate = LocalDate.of(year, Month.AUGUST, this)
internal infix fun Int.september(year: Int): LocalDate = LocalDate.of(year, Month.SEPTEMBER, this)
internal infix fun Int.oktober(year: Int): LocalDate = LocalDate.of(year, Month.OCTOBER, this)
internal infix fun Int.november(year: Int): LocalDate = LocalDate.of(year, Month.NOVEMBER, this)
internal infix fun Int.desember(year: Int): LocalDate = LocalDate.of(year, Month.DECEMBER, this)

fun nå(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
