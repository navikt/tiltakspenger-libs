package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodiseringOverlappTest {
    private val start = LocalDate.of(2024, 1, 1)
    private val slutt = LocalDate.of(2024, 12, 31)

    @Test
    fun `overlapperMed - innsendt periode lik total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        periodisering.overlapperMed(originalPeriode) shouldBe Periodisering(verdi, originalPeriode)
    }

    @Test
    fun `overlapperMed - innsendt periode overlapper ikke med total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeUtenOverlapp = Periode(slutt.plusDays(3), slutt.plusMonths(3))
        periodisering.overlapperMed(periodeUtenOverlapp) shouldBe Periodisering(emptyList())
    }

    @Test
    fun `overlapperMed - innsendt periode overlapper med starten av total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeMedOverlapp = Periode(start.minusDays(3), start.plusDays(3))
        periodisering.overlapperMed(periodeMedOverlapp) shouldBe Periodisering(verdi, originalPeriode)
    }

    @Test
    fun `overlapperMed - innsendt periode overlapper med slutten av total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeMedOverlapp = Periode(slutt.minusDays(3), slutt.plusDays(3))
        periodisering.overlapperMed(periodeMedOverlapp) shouldBe Periodisering(verdi, originalPeriode)
    }

    @Test
    fun `overlapperMed - innsendt periode overlapper med hele total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeMedOverlapp = Periode(start.minusDays(3), slutt.plusDays(3))
        periodisering.overlapperMed(periodeMedOverlapp) shouldBe Periodisering(verdi, originalPeriode)
    }

    @Test
    fun `overlapperMed - innsendt periode overlapper med del av total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeMedOverlapp = Periode(start.plusDays(3), slutt.minusDays(3))
        periodisering.overlapperMed(periodeMedOverlapp) shouldBe Periodisering(verdi, originalPeriode)
    }

    @Test
    fun `overlappendePeriode - innsendt periode lik total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        periodisering.overlappendePeriode(originalPeriode) shouldBe Periodisering(verdi, originalPeriode)
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper ikke med total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeUtenOverlapp = Periode(slutt.plusDays(3), slutt.plusMonths(3))
        periodisering.overlappendePeriode(periodeUtenOverlapp) shouldBe Periodisering(emptyList())
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper med starten av total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeMedOverlapp = Periode(start.minusDays(3), start.plusDays(3))
        periodisering.overlappendePeriode(periodeMedOverlapp) shouldBe Periodisering(verdi, Periode(originalPeriode.fraOgMed, periodeMedOverlapp.tilOgMed))
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper med slutten av total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeMedOverlapp = Periode(slutt.minusDays(3), slutt.plusDays(3))
        periodisering.overlappendePeriode(periodeMedOverlapp) shouldBe Periodisering(verdi, Periode(periodeMedOverlapp.fraOgMed, originalPeriode.tilOgMed))
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper med hele total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeMedOverlapp = Periode(start.minusDays(3), slutt.plusDays(3))
        periodisering.overlappendePeriode(periodeMedOverlapp) shouldBe Periodisering(verdi, originalPeriode)
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper med del av total periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        val periodeMedOverlapp = Periode(start.plusDays(3), slutt.minusDays(3))
        periodisering.overlappendePeriode(periodeMedOverlapp) shouldBe Periodisering(verdi, periodeMedOverlapp)
    }
}
