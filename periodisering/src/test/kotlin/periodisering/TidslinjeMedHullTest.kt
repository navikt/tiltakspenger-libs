package no.nav.tiltakspenger.libs.periodisering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.libs.dato.februar
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.dato.rangeTo
import no.nav.tiltakspenger.libs.periodisering.TestVedtak.Resultat.INNVILGET
import no.nav.tiltakspenger.libs.periodisering.TestVedtak.Resultat.OPPHØRT
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TidslinjeMedHullTest {

    @Test
    fun `en til en`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        listOf(v1).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v1, v1.periode),
        )
    }

    @Test
    fun `to like tar siste`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = INNVILGET,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v2, v2.periode),
        )
    }

    @Test
    fun `støtter ikke duplikat tidspunkt`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        shouldThrow<IllegalArgumentException> {
            listOf(v1, v1).toTidslinje()
        }.message.shouldContain("Støtter ikke lage tidslinje når 2 elementer er opprettet samtidig.")
    }

    @Test
    fun `ingen overlapp`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 2, 1),
            tom = LocalDate.of(2024, 2, 29),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v1, v1.periode),
            PeriodeMedVerdi(v2, v2.periode),
        )
    }

    @Test
    fun `fullstendig overlapp`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v2, v2.periode),
        )
    }

    @Test
    fun `midtre overlapp`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 2),
            tom = LocalDate.of(2024, 1, 30),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v1, 1.januar(2024)..1.januar(2024)),
            PeriodeMedVerdi(v2, 2.januar(2024)..30.januar(2024)),
            PeriodeMedVerdi(v1, 31.januar(2024)..31.januar(2024)),
        )
    }

    @Test
    fun `overlapp første del`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 1),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v2, 1.januar(2024)..1.januar(2024)),
            PeriodeMedVerdi(v1, 2.januar(2024)..31.januar(2024)),
        )
    }

    @Test
    fun `overlapp siste del`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 31),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v1, 1.januar(2024)..30.januar(2024)),
            PeriodeMedVerdi(v2, 31.januar(2024)..31.januar(2024)),
        )
    }

    @Test
    fun `delvis overlapp første del`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 2),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 3),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v2, 1.januar(2024)..3.januar(2024)),
            PeriodeMedVerdi(v1, 4.januar(2024)..31.januar(2024)),
        )
    }

    @Test
    fun `delvis overlapp siste del`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 30),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 29),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v1, 1.januar(2024)..28.januar(2024)),
            PeriodeMedVerdi(v2, 29.januar(2024)..31.januar(2024)),
        )
    }

    @Test
    fun `fullstendig overlapp med extension på begge sider`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 2),
            tom = LocalDate.of(2024, 1, 30),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        listOf(v1, v2).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v2, 1.januar(2024)..31.januar(2024)),
        )
    }

    @Test
    fun `Støtter fram og tilbake`() {
        val v1 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )
        val v3 = TestVedtak(
            fom = LocalDate.of(2024, 1, 1),
            tom = LocalDate.of(2024, 1, 31),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.002"),
            resultat = INNVILGET,
        )
        listOf(v1, v2, v3).toTidslinje() shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(v3, 1.januar(2024)..31.januar(2024)),
        )
    }

    @Test
    fun `hull mellom periodene blir fyllt inn med nulls`() {
        val v1 = TestVedtak(
            fom = 1.januar(2024),
            tom = 31.januar(2024),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.000"),
            resultat = INNVILGET,
        )
        val v2 = TestVedtak(
            fom = 2.februar(2024),
            tom = 29.februar(2024),
            opprettet = LocalDateTime.parse("2021-01-01T00:00:00.001"),
            resultat = OPPHØRT,
        )

        listOf(v1, v2).toTidslinje() shouldBe IkkesammenhengendePeriodisering(
            PeriodeMedVerdi(v1, 1.januar(2024)..31.januar(2024)),
            PeriodeMedVerdi(v2, 2.februar(2024)..29.februar(2024)),
        )
    }
}
