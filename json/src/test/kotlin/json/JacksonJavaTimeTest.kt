package no.nav.tiltakspenger.libs.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tools.jackson.databind.DatabindException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * SerDes-kontrakt for `java.time`-typer.
 * Vi asserterer **eksakt JSON-format** (ikke bare round-trip) for å fange konfigurasjons-drift i [objectMapper] — symmetrisk brutte sider av serialize/deserialize kan henge sammen uten at JSON-formatet stemmer med det vi tror.
 *
 * Speiler tilsvarende tester i `httpklient` slik at vi fanger endringer begge steder.
 */
internal class JacksonJavaTimeTest {

    // --- "Enkle" java.time-typer (én forventning per type) -----------------------------------

    @Test
    fun `LocalDate round-trip`() {
        roundTrip(LocalDate.of(2026, 1, 15), """"2026-01-15"""")
    }

    @Test
    fun `LocalTime round-trip`() {
        roundTrip(LocalTime.of(12, 34, 56), """"12:34:56"""")
    }

    @Test
    fun `Instant round-trip`() {
        roundTrip(Instant.parse("2026-01-15T12:34:56Z"), """"2026-01-15T12:34:56Z"""")
    }

    @Test
    fun `Instant med nanosekunder bevares`() {
        roundTrip(
            Instant.parse("2026-01-15T12:34:56.123456789Z"),
            """"2026-01-15T12:34:56.123456789Z"""",
        )
    }

    @Test
    fun `Year, YearMonth og MonthDay`() {
        roundTrip(Year.of(2026), "2026")
        roundTrip(YearMonth.of(2026, 1), """"2026-01"""")
        roundTrip(MonthDay.of(1, 15), """"--01-15"""")
    }

    @Test
    fun `ZoneId og ZoneOffset`() {
        roundTrip(ZoneId.of("Europe/Oslo"), """"Europe/Oslo"""")
        roundTrip(ZoneOffset.ofHours(2), """"+02:00"""")
    }

    @Test
    fun `java time Duration — alle enheter`() {
        // java.time.Duration sin ISO-8601-toString bruker kun timer/minutter/sekunder/nanos — aldri P1D/P1W/P1M/P1Y.
        // Dager (eller lengre tidsspenn) rendres derfor som timer.
        roundTrip(java.time.Duration.ofNanos(1), """"PT0.000000001S"""")
        roundTrip(java.time.Duration.ofMillis(1), """"PT0.001S"""")
        roundTrip(java.time.Duration.ofSeconds(1), """"PT1S"""")
        roundTrip(java.time.Duration.ofMinutes(1), """"PT1M"""")
        roundTrip(java.time.Duration.ofHours(1), """"PT1H"""")
        roundTrip(java.time.Duration.ofDays(1), """"PT24H"""")
        // Sammensatt:
        roundTrip(java.time.Duration.ofSeconds(90), """"PT1M30S"""")
    }

    @Test
    fun `java time Period — alle enheter`() {
        // Period bruker P-format med Y/M/D — IKKE H/M/S.
        // Merk: "M" i Period betyr måneder; "M" i Duration betyr minutter. T-prefikset i Duration ("PT...") disambiguerer.
        roundTrip(java.time.Period.ofDays(1), """"P1D"""")
        roundTrip(java.time.Period.ofMonths(1), """"P1M"""")
        roundTrip(java.time.Period.ofYears(1), """"P1Y"""")
        roundTrip(java.time.Period.ofYears(10), """"P10Y"""") // tiår
        roundTrip(java.time.Period.ofYears(100), """"P100Y"""") // århundre
        roundTrip(java.time.Period.ofYears(1000), """"P1000Y"""") // millennium
        roundTrip(java.time.Period.of(1, 2, 3), """"P1Y2M3D"""")
    }

    @Test
    fun `java time Period — uker normaliseres til dager på wire`() {
        // Period.ofWeeks(n) lagres internt som n*7 dager — uker er ikke en egen field på Period.
        // toString() gir alltid "P{n}D" for uker, ikke "P1W".
        roundTrip(java.time.Period.ofWeeks(1), """"P7D"""")
        // På input aksepterer Period.parse(...) "P1W" og konverterer til 7 dager:
        deserialize<java.time.Period>(""""P1W"""") shouldBe java.time.Period.ofDays(7)
    }

    // --- LocalDateTime: alle container-varianter --------------------------------------------

    private val ldt = LocalDateTime.of(2026, 1, 15, 12, 34, 56)
    private val ldt2 = LocalDateTime.of(2026, 6, 15, 8, 0, 0)
    private val ldtNano = LocalDateTime.of(2026, 1, 15, 12, 34, 56, 123_000_000)

    @Test
    fun `LocalDateTime top-level`() {
        roundTrip(ldt, """"2026-01-15T12:34:56"""")
    }

    @Test
    fun `LocalDateTime med nanosekunder bevares`() {
        roundTrip(ldtNano, """"2026-01-15T12:34:56.123"""")
    }

    @Test
    fun `LocalDateTime som felt i DTO`() {
        roundTrip(
            MedLocalDateTime(navn = "a", tid = ldt),
            """{"navn":"a","tid":"2026-01-15T12:34:56"}""",
        )
    }

    @Test
    fun `LocalDateTime nullable felt i DTO`() {
        roundTrip(
            MedLocalDateTimeNullable("a", ldt),
            """{"navn":"a","tid":"2026-01-15T12:34:56"}""",
        )
        roundTrip(
            MedLocalDateTimeNullable("a", null),
            """{"navn":"a","tid":null}""",
        )
        // Manglende felt = null for nullable.
        deserialize<MedLocalDateTimeNullable>("""{"navn":"a"}""") shouldBe
            MedLocalDateTimeNullable("a", null)
    }

    @Test
    fun `List av LocalDateTime`() {
        roundTripList(
            listOf(ldt, ldt2),
            """["2026-01-15T12:34:56","2026-06-15T08:00:00"]""",
        )
    }

    @Test
    fun `Map av String til LocalDateTime`() {
        roundTripMap(
            mapOf("vinter" to ldt, "sommer" to ldt2),
            """{"vinter":"2026-01-15T12:34:56","sommer":"2026-06-15T08:00:00"}""",
        )
    }

    @Test
    fun `nestet DTO med liste av DTO-er med LocalDateTime`() {
        roundTrip(
            NestetMedLocalDateTime(
                tittel = "ytre",
                elementer = listOf(
                    MedLocalDateTime(navn = "a", tid = ldt),
                    MedLocalDateTime(navn = "b", tid = ldt2),
                ),
            ),
            """{"tittel":"ytre","elementer":[""" +
                """{"navn":"a","tid":"2026-01-15T12:34:56"},""" +
                """{"navn":"b","tid":"2026-06-15T08:00:00"}""" +
                """]}""",
        )
    }

    @Test
    fun `Map som verdi i DTO med LocalDateTime`() {
        roundTrip(
            MedLocalDateTimeMap(navn = "x", tider = mapOf("a" to ldt, "b" to ldt2)),
            """{"navn":"x","tider":{"a":"2026-01-15T12:34:56","b":"2026-06-15T08:00:00"}}""",
        )
    }

    // --- ZonedDateTime og OffsetDateTime ----------------------------------------------------
    //
    // JSON-formatet er strikt ISO-8601 uten `[Europe/Oslo]`-suffix.
    // Round-trip mister zone-id; bare Instant er bevart.
    // ADJUST_DATES_TO_CONTEXT_TIME_ZONE er på som default, så deserialiserte verdier normaliseres til UTC.
    // Bruk eget felt for ZoneId hvis den må bevares.

    private val oslo = ZoneId.of("Europe/Oslo")
    private val zdtVinter = ZonedDateTime.of(LocalDateTime.of(2026, 1, 15, 12, 34, 56), oslo)
    private val zdtSommer = ZonedDateTime.of(LocalDateTime.of(2026, 6, 15, 12, 34, 56), oslo)

    @Test
    fun `OffsetDateTime serialiseres med original offset, normaliseres til UTC ved deserialisering`() {
        val medOffset = OffsetDateTime.of(2026, 1, 15, 12, 34, 56, 0, ZoneOffset.ofHours(2))
        serialize(medOffset) shouldBe """"2026-01-15T12:34:56+02:00""""

        val lest = deserialize<OffsetDateTime>(""""2026-01-15T12:34:56+02:00"""")
        lest.toInstant() shouldBe medOffset.toInstant()
        lest.offset shouldBe ZoneOffset.UTC
    }

    @Test
    fun `ZonedDateTime serialiseres uten zone-id-suffix, Instant bevares`() {
        serialize(zdtVinter) shouldBe """"2026-01-15T12:34:56+01:00""""
        serialize(zdtSommer) shouldBe """"2026-06-15T12:34:56+02:00""""

        deserialize<ZonedDateTime>(""""2026-01-15T12:34:56+01:00"""")
            .toInstant() shouldBe zdtVinter.toInstant()
        deserialize<ZonedDateTime>(""""2026-06-15T12:34:56+02:00"""")
            .toInstant() shouldBe zdtSommer.toInstant()
    }

    @Test
    fun `ZonedDateTime som felt i DTO normaliseres til UTC`() {
        val dto = MedZonedDateTime(navn = "a", tid = zdtVinter)
        val json = """{"navn":"a","tid":"2026-01-15T12:34:56+01:00"}"""
        serialize(dto) shouldBe json

        val lest = deserialize<MedZonedDateTime>(json)
        lest.navn shouldBe "a"
        lest.tid.toInstant() shouldBe zdtVinter.toInstant()
        lest.tid.zone shouldBe ZoneOffset.UTC
    }

    @Test
    fun `ZonedDateTime nullable felt i DTO`() {
        serialize(MedZonedDateTimeNullable("a", zdtVinter)) shouldBe
            """{"navn":"a","tid":"2026-01-15T12:34:56+01:00"}"""
        roundTrip(MedZonedDateTimeNullable("a", null), """{"navn":"a","tid":null}""")
        deserialize<MedZonedDateTimeNullable>("""{"navn":"a"}""") shouldBe
            MedZonedDateTimeNullable("a", null)
    }

    @Test
    fun `List og Map av ZonedDateTime`() {
        val liste = listOf(zdtVinter, zdtSommer)
        liste.serialize() shouldBe
            """["2026-01-15T12:34:56+01:00","2026-06-15T12:34:56+02:00"]"""
        deserializeList<ZonedDateTime>(liste.serialize()).map { it.toInstant() } shouldBe
            liste.map { it.toInstant() }

        val map = mapOf("vinter" to zdtVinter, "sommer" to zdtSommer)
        serialize(map) shouldBe
            """{"vinter":"2026-01-15T12:34:56+01:00","sommer":"2026-06-15T12:34:56+02:00"}"""
    }

    @Test
    fun `UTC kan leses fra flere formater`() {
        val instant = Instant.parse("2026-06-15T10:34:56Z")
        deserialize<Instant>("\"2026-06-15T10:34:56Z\"") shouldBe instant
        deserialize<OffsetDateTime>("\"2026-06-15T10:34:56Z\"").toInstant() shouldBe instant
        deserialize<OffsetDateTime>("\"2026-06-15T10:34:56+00:00\"").toInstant() shouldBe instant
        deserialize<ZonedDateTime>("\"2026-06-15T10:34:56Z[UTC]\"").toInstant() shouldBe instant
    }

    @Test
    fun `Clock med Oslo-zone gir Oslo-lokal ZonedDateTime og UTC Instant`() {
        val instant = Instant.parse("2026-06-15T10:34:56Z")
        val clock = Clock.fixed(instant, oslo)

        Instant.now(clock) shouldBe instant
        serialize(Instant.now(clock)) shouldBe """"2026-06-15T10:34:56Z""""
        serialize(ZonedDateTime.now(clock)) shouldBe """"2026-06-15T12:34:56+02:00""""
        ZonedDateTime.now(clock).toInstant() shouldBe instant
    }

    // --- Cross-type fail-loud ----------------------------------------------------------------

    @Test
    fun `LocalDateTime-streng kan ikke leses som ZonedDateTime — mangler offset`() {
        shouldThrow<DatabindException> {
            deserialize<ZonedDateTime>(""""2026-01-15T12:34:56"""")
        }
    }

    @Test
    fun `ZonedDateTime-streng kan ikke leses som LocalDateTime — har offset`() {
        // Fail-loud: en avsender som ved en feil sender ZonedDateTime-format til et LocalDateTime-felt får en eksplisitt feil i stedet for stille datatap.
        shouldThrow<DatabindException> {
            deserialize<LocalDateTime>(""""2026-01-15T12:34:56+01:00"""")
        }
    }

    @Test
    fun `DTO med både LocalDateTime og ZonedDateTime`() {
        val dto = MedBegge(navn = "blandet", lokal = ldt, sonet = zdtVinter)
        val json = """{"navn":"blandet","lokal":"2026-01-15T12:34:56","sonet":"2026-01-15T12:34:56+01:00"}"""
        serialize(dto) shouldBe json

        val lest = deserialize<MedBegge>(json)
        lest.lokal shouldBe ldt
        lest.sonet.toInstant() shouldBe zdtVinter.toInstant()
    }
}

private data class MedLocalDateTime(val navn: String, val tid: LocalDateTime)
private data class MedLocalDateTimeNullable(val navn: String, val tid: LocalDateTime?)
private data class MedLocalDateTimeMap(val navn: String, val tider: Map<String, LocalDateTime>)
private data class NestetMedLocalDateTime(val tittel: String, val elementer: List<MedLocalDateTime>)
private data class MedZonedDateTime(val navn: String, val tid: ZonedDateTime)
private data class MedZonedDateTimeNullable(val navn: String, val tid: ZonedDateTime?)
private data class MedBegge(val navn: String, val lokal: LocalDateTime, val sonet: ZonedDateTime)
