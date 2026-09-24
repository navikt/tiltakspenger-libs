package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class KlokkeTest {

    @Test
    fun `fixedClock står stille i UTC`() {
        fixedClock.instant() shouldBe Instant.parse("2025-01-01T01:02:03.456789Z")
        fixedClock.instant() shouldBe Instant.parse("2025-01-01T01:02:03.456789Z")
        fixedClock.zone shouldBe ZoneOffset.UTC
    }

    @Test
    fun `fixedClockAt uten argument gir samme tidspunkt som fixedClock`() {
        fixedClockAt().instant() shouldBe fixedClock.instant()
    }

    @Test
    fun `fixedClockAt med dato beholder klokkeslettet fra fixedClock`() {
        fixedClockAt(LocalDate.of(2026, 3, 14)).instant() shouldBe Instant.parse("2026-03-14T01:02:03.456789Z")
    }

    @Test
    fun `fixedClockAt med tidspunkt treffer tidspunktet`() {
        fixedClockAt(LocalDateTime.of(2026, 3, 14, 20, 49)).instant() shouldBe Instant.parse("2026-03-14T20:49:00Z")
    }

    @Test
    fun `enUkeEtterFixedClock ligger sju dager etter fixedClock`() {
        enUkeEtterFixedClock.instant() shouldBe Instant.parse("2025-01-08T01:02:03.456789Z")
        enUkeEtterFixedClock.zone shouldBe ZoneOffset.UTC
    }

    @Test
    fun `plus gir en ny fast klokke i UTC`() {
        val toTimerEtter = fixedClock.plus(2, ChronoUnit.HOURS)
        toTimerEtter.instant() shouldBe Instant.parse("2025-01-01T03:02:03.456789Z")
        toTimerEtter.zone shouldBe ZoneOffset.UTC
    }

    @Test
    fun `hver avlesning flytter klokka ett sekund`() {
        val klokke = TikkendeKlokke()
        klokke.instant() shouldBe Instant.parse("2025-01-01T01:02:04.456789Z")
        klokke.instant() shouldBe Instant.parse("2025-01-01T01:02:05.456789Z")
    }

    @Test
    fun `steget per avlesning kan settes i konstruktøren`() {
        val klokke = TikkendeKlokke(stegPerAvlesning = 5.minutes)
        klokke.instant() shouldBe Instant.parse("2025-01-01T01:07:03.456789Z")
        klokke.instant() shouldBe Instant.parse("2025-01-01T01:12:03.456789Z")
    }

    @Test
    fun `sonen og starttidspunktet kommer fra klokka den startet fra`() {
        val oslo = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneId.of("Europe/Oslo"))
        val klokke = TikkendeKlokke(oslo)

        klokke.zone shouldBe ZoneId.of("Europe/Oslo")
        klokke.spolTil(LocalDate.of(2025, 1, 2)) shouldBe Instant.parse("2025-01-01T23:00:00Z")

        val iUtc = klokke.withZone(ZoneOffset.UTC)
        iUtc.zone shouldBe ZoneOffset.UTC
        iUtc.instant() shouldBe Instant.parse("2025-01-01T00:00:00Z")
    }

    @Test
    fun `spolTil et tidspunkt setter klokka til tidspunktet`() {
        val klokke = TikkendeKlokke(fixedClockAt(LocalDate.of(2025, 5, 20)))

        klokke.spolTil(LocalDateTime.of(2025, 5, 20, 20, 49)) shouldBe Instant.parse("2025-05-20T20:49:00Z")
        klokke.instant() shouldBe Instant.parse("2025-05-20T20:49:01Z")
    }

    @Test
    fun `spolTil en dato lander på midnatt og beholder brøkdelen av sekundet`() {
        val klokke = TikkendeKlokke()

        klokke.spolTil(LocalDate.of(2025, 2, 1)) shouldBe Instant.parse("2025-02-01T00:00:00.456789Z")
        klokke.instant() shouldBe Instant.parse("2025-02-01T00:00:01.456789Z")
    }

    @Test
    fun `spolTil en dato bakover feiler`() {
        val feil = shouldThrow<IllegalArgumentException> {
            TikkendeKlokke().spolTil(LocalDate.of(2024, 12, 31))
        }

        feil.message shouldBe
            "Kan ikke spole bakover: 2024-12-31T00:00:00.456789Z er ikke etter 2025-01-01T01:02:03.456789Z"
    }

    @Test
    fun `spolTil et tidspunkt bakover feiler`() {
        val feil = shouldThrow<IllegalArgumentException> {
            TikkendeKlokke().spolTil(LocalDateTime.of(2025, 1, 1, 1, 0))
        }

        feil.message shouldBe
            "Kan ikke spole bakover: 2025-01-01T01:00:00Z er ikke etter 2025-01-01T01:02:03.456789Z"
    }

    @Test
    fun `spolFrem flytter klokka en vilkårlig varighet`() {
        val klokke = TikkendeKlokke()

        klokke.spolFrem(90.minutes) shouldBe Instant.parse("2025-01-01T02:32:03.456789Z")
        klokke.spolFrem(500.milliseconds) shouldBe Instant.parse("2025-01-01T02:32:03.956789Z")
    }

    @Test
    fun `spolFrem uten positiv varighet feiler`() {
        shouldThrow<IllegalArgumentException> {
            TikkendeKlokke().spolFrem(Duration.ZERO)
        }.message shouldBe "Varigheten må være positiv: 0s"

        shouldThrow<IllegalArgumentException> {
            TikkendeKlokke().spolFrem((-1).seconds)
        }.message shouldBe "Varigheten må være positiv: -1s"
    }

    @Test
    fun `spol1timeFrem legger til nøyaktig én time`() {
        val klokke = TikkendeKlokke()

        klokke.spol1timeFrem() shouldBe Instant.parse("2025-01-01T02:02:03.456789Z")
        klokke.spol1timeFrem() shouldBe Instant.parse("2025-01-01T03:02:03.456789Z")
    }

    @Test
    fun `copy gir en ny klokke fra samme utgangspunkt og med samme steg`() {
        val klokke = TikkendeKlokke(stegPerAvlesning = 2.seconds)
        klokke.spol1timeFrem()

        val kopi = klokke.copy()

        kopi.instant() shouldBe Instant.parse("2025-01-01T01:02:05.456789Z")
        klokke.instant() shouldBe Instant.parse("2025-01-01T02:02:05.456789Z")
    }

    @Test
    fun `tidskilden rykker ett steg for hver avlesning`() {
        val markering = TikkendeTidskilde().markNow()

        markering.elapsedNow() shouldBe 1.seconds
        markering.elapsedNow() shouldBe 2.seconds
    }

    @Test
    fun `markeringer deler den forløpte tiden i tidskilden`() {
        val tidskilde = TikkendeTidskilde(stegPerAvlesning = 10.milliseconds)
        val første = tidskilde.markNow()
        første.elapsedNow() shouldBe 10.milliseconds

        val andre = tidskilde.markNow()

        andre.elapsedNow() shouldBe 10.milliseconds
        første.elapsedNow() shouldBe 30.milliseconds
    }
}
