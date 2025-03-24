package no.nav.tiltakspenger.libs.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

private val startPoint = LocalDate.parse("2025-01-01")

/** Fixed UTC Clock at 2025-01-01T01:02:03.456789000Z */
val fixedClock: Clock = Clock.fixed(startPoint.atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

fun fixedClockAt(date: LocalDate = startPoint): Clock =
    Clock.fixed(date.atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

/** Fixed UTC clock at 2025-02-08T01:02:03.456789000Z */
val enUkeEtterFixedClock: Clock = fixedClock.plus(7, ChronoUnit.DAYS)

/** Fixed UTC Clock */
fun Clock.plus(amountToAdd: Long, unit: TemporalUnit): Clock =
    Clock.fixed(this.instant().plus(amountToAdd, unit), ZoneOffset.UTC)

class TikkendeKlokke(
    private val initialClock: Clock = fixedClock,
) : Clock() {
    private var nextInstant = initialClock.instant()

    override fun getZone(): ZoneId = initialClock.zone
    override fun withZone(zone: ZoneId?): Clock = initialClock.withZone(zone)

    override fun instant(): Instant {
        nextInstant = nextInstant.plus(1, ChronoUnit.SECONDS)
        return nextInstant
    }

    fun spolTil(dato: LocalDate): Instant {
        require(dato.atStartOfDay(zone).toInstant() > nextInstant) { "Kan bare spole fremover i tid" }
        return dato.atStartOfDay(zone).plus(nextInstant.nano.toLong(), ChronoUnit.NANOS).toInstant().also {
            nextInstant = it
        }
    }

    fun copy(): TikkendeKlokke = TikkendeKlokke(initialClock)
}
