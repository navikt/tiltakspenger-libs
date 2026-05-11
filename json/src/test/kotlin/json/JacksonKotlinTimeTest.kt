package no.nav.tiltakspenger.libs.json

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * SerDes-kontrakt for `kotlin.time.Duration`.
 *
 * Jackson Kotlin-modulen er konfigurert med [tools.jackson.module.kotlin.KotlinFeature.UseJavaDurationConversion], som mapper `kotlin.time.Duration` til/fra `java.time.Duration`.
 * JSON-formatet er ISO-8601 (`"PT1M30S"`), samme som [java.time.Duration].
 */
internal class JacksonKotlinTimeTest {

    @Test
    fun `Duration — alle stdlib-enheter`() {
        // kotlin.time.Duration konverteres til java.time.Duration på wire, og java.time.Duration sitt ISO-8601-format bruker kun H/M/S — aldri P1D/P1W/P1M/P1Y.
        // Dager (og lengre tidsspenn man kan uttrykke som dager) rendres derfor som timer.
        roundTrip(1.nanoseconds, """"PT0.000000001S"""")
        roundTrip(1.microseconds, """"PT0.000001S"""")
        roundTrip(1.milliseconds, """"PT0.001S"""")
        roundTrip(1.seconds, """"PT1S"""")
        roundTrip(1.minutes, """"PT1M"""")
        roundTrip(1.hours, """"PT1H"""")
        roundTrip(1.days, """"PT24H"""")
    }

    @Test
    fun `Duration — kalenderlignende spenn rendres som timer, ikke kalenderenheter`() {
        // Kalenderbegreper som uke/måned/år/tiår/århundre/millennium har ingen eksakt varighet (skuddår, sommertid m.m.) og finnes ikke i kotlin.time.Duration.
        // Hvis koden vår uttrykker dem som dager, ender de opp som timer på wire — bruk [java.time.Period] hvis du trenger kalenderenheter.
        roundTrip(7.days, """"PT168H"""") // 1 uke
        roundTrip(30.days, """"PT720H"""") // ~1 måned
        roundTrip(365.days, """"PT8760H"""") // ~1 år
        roundTrip((10 * 365).days, """"PT87600H"""") // ~1 tiår
    }

    @Test
    fun `Duration — sammensatte verdier`() {
        roundTrip(90.seconds, """"PT1M30S"""")
        roundTrip(123.milliseconds, """"PT0.123S"""")
        roundTrip(2.hours + 30.minutes, """"PT2H30M"""")
        roundTrip(1.hours + 1.minutes + 1.seconds, """"PT1H1M1S"""")
    }

    @Test
    fun `Duration som felt i DTO`() {
        roundTrip(
            MedKotlinDuration(navn = "timeout", varighet = 90.seconds),
            """{"navn":"timeout","varighet":"PT1M30S"}""",
        )
    }

    @Test
    fun `Duration i List og Map`() {
        roundTripList(
            listOf(1.seconds, 2.minutes),
            """["PT1S","PT2M"]""",
        )
        roundTripMap(
            mapOf("kort" to 1.seconds, "lang" to 2.minutes),
            """{"kort":"PT1S","lang":"PT2M"}""",
        )
    }

    @Test
    fun `Duration som element i DTO-liste`() {
        roundTrip(
            listOf(MedKotlinDuration(navn = "a", varighet = 1.seconds)),
            """[{"navn":"a","varighet":"PT1S"}]""",
        )
    }
}
private data class MedKotlinDuration(
    val navn: String,
    val varighet: Duration,
)
