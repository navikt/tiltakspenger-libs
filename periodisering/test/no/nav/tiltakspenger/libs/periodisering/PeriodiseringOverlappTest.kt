package no.nav.tiltakspenger.libs.standardperiodisering

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.desember
import no.nav.tiltakspenger.libs.dato.februar
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.dato.mars
import no.nav.tiltakspenger.libs.periode.Periode
import no.nav.tiltakspenger.libs.periode.somPeriode
import no.nav.tiltakspenger.libs.periode.til
import no.nav.tiltakspenger.libs.periodisering.IkkesammenhengendePeriodisering
import no.nav.tiltakspenger.libs.periodisering.PeriodeMedVerdi
import no.nav.tiltakspenger.libs.periodisering.Periodisering
import no.nav.tiltakspenger.libs.periodisering.SammenhengendePeriodisering
import org.junit.jupiter.api.Test

class PeriodiseringOverlappTest {
    private val start = 1.januar(2024)
    private val slutt = 31.desember(2024)
    private val år2024 = Periode(start, slutt)
    val standardverdi = "test"
    val standardperiodisering = SammenhengendePeriodisering(standardverdi, år2024)

    @Test
    fun `overlapper - periode - innsendt periode lik total periode`() {
        standardperiodisering.overlapper(år2024) shouldBe true
    }

    @Test
    fun `overlapper - periode - innsendt periode overlapper ikke med total periode`() {
        val periodeUtenOverlapp = Periode(slutt.plusDays(3), slutt.plusMonths(3))
        standardperiodisering.overlapper(periodeUtenOverlapp) shouldBe false
    }

    @Test
    fun `overlapper - periode - innsendt periode overlapper med starten av total periode`() {
        val periodeMedOverlapp = Periode(start.minusDays(3), start.plusDays(3))
        standardperiodisering.overlapper(periodeMedOverlapp) shouldBe true
    }

    @Test
    fun `overlapper - periode - innsendt periode overlapper med slutten av total periode`() {
        val periodeMedOverlapp = Periode(slutt.minusDays(3), slutt.plusDays(3))
        standardperiodisering.overlapper(periodeMedOverlapp) shouldBe true
    }

    @Test
    fun `overlapper - periode - innsendt periode overlapper med hele total periode`() {
        val periodeMedOverlapp = Periode(start.minusDays(3), slutt.plusDays(3))
        standardperiodisering.overlapper(periodeMedOverlapp) shouldBe true
    }

    @Test
    fun `overlapper - periode - innsendt periode overlapper med del av total periode`() {
        val periodeMedOverlapp = Periode(start.plusDays(3), slutt.minusDays(3))
        standardperiodisering.overlapper(periodeMedOverlapp) shouldBe true
    }

    @Test
    fun `overlapper - periode - ikke-sammenhengende overlapper ikke i hullet`() {
        val periodisering = IkkesammenhengendePeriodisering(
            PeriodeMedVerdi(standardverdi, 1 til 31.januar(2025)),
            PeriodeMedVerdi(standardverdi, 1 til 31.mars(2025)),
        )
        standardperiodisering.overlapper(1 til 28.februar(2025)) shouldBe false
    }

    @Test
    fun `overlapper - periodisering - like periodiseringer`() {
        standardperiodisering.overlapper(standardperiodisering) shouldBe true
    }

    @Test
    fun `overlapper - periodisering - utenfor`() {
        standardperiodisering.overlapper(Periodisering(standardverdi, 31.desember(2023).somPeriode())) shouldBe false
        standardperiodisering.overlapper(Periodisering(standardverdi, 1.januar(2025).somPeriode())) shouldBe false
    }

    @Test
    fun `overlappendePeriode - innsendt periode lik total periode`() {
        standardperiodisering.overlappendePeriode(år2024) shouldBe SammenhengendePeriodisering(
            standardverdi,
            år2024,
        )
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper ikke med total periode`() {
        val periodeUtenOverlapp = Periode(slutt.plusDays(3), slutt.plusMonths(3))
        standardperiodisering.overlappendePeriode(periodeUtenOverlapp) shouldBe Periodisering.empty()
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper med starten av total periode`() {
        val periodeMedOverlapp = Periode(start.minusDays(3), start.plusDays(3))
        standardperiodisering.overlappendePeriode(periodeMedOverlapp) shouldBe SammenhengendePeriodisering(
            standardverdi,
            Periode(år2024.fraOgMed, periodeMedOverlapp.tilOgMed),
        )
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper med slutten av total periode`() {
        val periodeMedOverlapp = Periode(slutt.minusDays(3), slutt.plusDays(3))
        standardperiodisering.overlappendePeriode(periodeMedOverlapp) shouldBe SammenhengendePeriodisering(
            standardverdi,
            Periode(periodeMedOverlapp.fraOgMed, år2024.tilOgMed),
        )
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper med hele total periode`() {
        val periodeMedOverlapp = Periode(start.minusDays(3), slutt.plusDays(3))
        standardperiodisering.overlappendePeriode(periodeMedOverlapp) shouldBe SammenhengendePeriodisering(
            standardverdi,
            år2024,
        )
    }

    @Test
    fun `overlappendePeriode - innsendt periode overlapper med del av total periode`() {
        val periodeMedOverlapp = Periode(start.plusDays(3), slutt.minusDays(3))
        standardperiodisering.overlappendePeriode(periodeMedOverlapp) shouldBe SammenhengendePeriodisering(
            standardverdi,
            periodeMedOverlapp,
        )
    }
}
