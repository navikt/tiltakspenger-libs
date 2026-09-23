package no.nav.tiltakspenger.libs.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

private val startPoint = LocalDate.parse("2025-01-01")

/** Fast UTC-klokke på 2025-01-01T01:02:03.456789000Z. */
val fixedClock: Clock = Clock.fixed(startPoint.atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

fun fixedClockAt(date: LocalDate = startPoint): Clock =
    Clock.fixed(date.atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

fun fixedClockAt(dateTime: LocalDateTime): Clock =
    Clock.fixed(dateTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

/** Fast UTC-klokke på 2025-01-08T01:02:03.456789000Z. */
val enUkeEtterFixedClock: Clock = fixedClock.plus(7, ChronoUnit.DAYS)

/** Lager en fast UTC-klokke flyttet [amountToAdd] [unit] fram fra denne klokka. */
fun Clock.plus(amountToAdd: Long, unit: TemporalUnit): Clock =
    Clock.fixed(this.instant().plus(amountToAdd, unit), ZoneOffset.UTC)

/**
 * Testklokke som flytter seg selv fremover, slik at en test får ulike tidsstempler uten å vente på ekte tid.
 * Hver avlesning flytter klokka [stegPerAvlesning] fremover og svarer med tidspunktet den havnet på.
 * Klokka går bare fremover: [spolTil] og [spolFrem] avviser et mål som ikke ligger etter tidspunktet klokka står på.
 * Tidssonen og starttidspunktet kommer fra [initialClock], og en [LocalDateTime] tolkes i den tidssonen.
 * Klokka er trådsikker, slik at tester som leser den fra flere tråder eller korutiner får hvert sitt tidsstempel.
 *
 * @param initialClock tidspunktet klokka starter på, og sonen den svarer i.
 * @param stegPerAvlesning hvor langt klokka flyttes for hver avlesning.
 */
class TikkendeKlokke(
    private val initialClock: Clock = fixedClock,
    private val stegPerAvlesning: Duration = 1.seconds,
) : Clock() {
    private val steg = stegPerAvlesning.toJavaDuration()
    private val gjeldendeTidspunkt = AtomicReference(initialClock.instant())

    override fun getZone(): ZoneId = initialClock.zone

    override fun withZone(zone: ZoneId?): Clock = initialClock.withZone(zone)

    /** Flytter klokka [stegPerAvlesning] fremover og svarer med tidspunktet den havnet på. */
    override fun instant(): Instant = gjeldendeTidspunkt.updateAndGet { it.plus(steg) }

    /**
     * Spoler til [tidspunkt], tolket i klokkas egen tidssone.
     * Neste avlesning svarer ett [stegPerAvlesning] senere enn [tidspunkt].
     */
    fun spolTil(tidspunkt: LocalDateTime): Instant = settTidspunkt { tidspunkt.atZone(zone).toInstant() }

    /**
     * Spoler til midnatt på [dato], tolket i klokkas egen sone.
     * Brøkdelen av sekundet klokka står på følger med, slik at tidsstemplene beholder samme underoppløsning gjennom testen.
     * Skal testen treffe et klokkeslett, bruk [spolTil] med et [LocalDateTime].
     */
    fun spolTil(dato: LocalDate): Instant =
        settTidspunkt { forrige -> dato.atStartOfDay(zone).plusNanos(forrige.nano.toLong()).toInstant() }

    /**
     * Flytter klokka [varighet] fremover, i tillegg til det avlesningene flytter den.
     * Svarer med tidspunktet klokka havnet på.
     */
    fun spolFrem(varighet: Duration): Instant {
        require(varighet.isPositive()) { "Varigheten må være positiv: $varighet" }
        return gjeldendeTidspunkt.updateAndGet { it.plus(varighet.toJavaDuration()) }
    }

    /** Flytter klokka én time fremover, som [spolFrem] med én time. */
    fun spol1timeFrem(): Instant = spolFrem(1.hours)

    /** Lager en klokke med samme utgangspunkt og samme steg, uten tiden denne klokka har brukt. */
    fun copy(): TikkendeKlokke = TikkendeKlokke(initialClock, stegPerAvlesning)

    private fun settTidspunkt(nyttTidspunkt: (Instant) -> Instant): Instant =
        gjeldendeTidspunkt.updateAndGet { forrige ->
            val nytt = nyttTidspunkt(forrige)
            require(nytt > forrige) { "Kan ikke spole bakover: $nytt er ikke etter $forrige" }
            nytt
        }
}

/**
 * [TimeSource]-motstykke til [TikkendeKlokke]: hver avlesning ([TimeMark.elapsedNow]) rykker den delte «forløpte tiden» ett fast [stegPerAvlesning] fremover.
 * Gir deterministiske varigheter i tester på samme måte som [TikkendeKlokke] gir deterministiske tidsstempler.
 * Tidskilden er trådsikker, slik at flere tråder eller korutiner kan lese den.
 */
class TikkendeTidskilde(
    private val stegPerAvlesning: Duration = 1.seconds,
) : TimeSource {
    private val forløpt = AtomicReference(Duration.ZERO)

    override fun markNow(): TimeMark {
        val vedMarkering = forløpt.get()
        return object : TimeMark {
            override fun elapsedNow(): Duration = forløpt.updateAndGet { it + stegPerAvlesning } - vedMarkering
        }
    }
}
