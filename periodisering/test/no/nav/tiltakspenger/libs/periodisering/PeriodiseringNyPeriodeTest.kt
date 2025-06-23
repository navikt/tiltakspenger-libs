package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Tar utgangspunkt i en meldeperiode som er før en vedtaksperiode, overlapper delvis med en vedtaksperiode eller overlapper helt med en vedtaksperiode.
 */
class PeriodiseringNyPeriodeTest {
    private val mandagUke1 = LocalDate.of(2025, 9, 1)
    private val mandagUke2 = LocalDate.of(2025, 9, 8)
    private val søndagUke2 = LocalDate.of(2025, 9, 14)

    @Test
    fun `ny periode lik original periode`() {
        val originalPeriode = Periode(mandagUke1, søndagUke2)
        val verdi = "test"
        val periodisering = SammenhengendePeriodisering(verdi, originalPeriode)
        periodisering.nyPeriode(originalPeriode, "default-verdi") shouldBe periodisering
    }

    @Test
    fun `krymping av av siste del av periode`() {
        val originalPeriode = Periode(mandagUke1, søndagUke2)
        val nyPeriode = Periode(mandagUke2, søndagUke2)
        val verdi = "test"
        val periodisering = SammenhengendePeriodisering(verdi, originalPeriode)
        periodisering.nyPeriode(nyPeriode, "default-verdi") shouldBe SammenhengendePeriodisering(verdi, nyPeriode)
    }

    @Test
    fun `krymping av av første del av periode`() {
        val originalPeriode = Periode(mandagUke1, søndagUke2)
        val nyPeriode = Periode(mandagUke1, mandagUke2)
        val verdi = "test"
        SammenhengendePeriodisering(
            verdi,
            originalPeriode,
        ).nyPeriode(nyPeriode, "default-verdi") shouldBe SammenhengendePeriodisering(verdi, nyPeriode)
    }

    @Test
    fun `krymper deler av første periode`() {
        val periode1 = Periode(mandagUke1, mandagUke2)
        val periode2 = Periode(mandagUke2.plusDays(1), søndagUke2)
        val nyPeriode = Periode(mandagUke2, søndagUke2)
        val verdi1 = "v1"
        val verdi2 = "v2"
        val periodisering = SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.nyPeriode(nyPeriode, "default-verdi") shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, Periode(mandagUke2, mandagUke2)),
            PeriodeMedVerdi(verdi2, periode2),
        )
    }

    @Test
    fun `fjerner periode pga krymping`() {
        val periode1 = Periode(mandagUke1, mandagUke2)
        val periode2 = Periode(mandagUke2.plusDays(1), søndagUke2)
        val nyPeriode = Periode(mandagUke1.plusDays(1), mandagUke2.minusDays(1))
        val verdi1 = "v1"
        val verdi2 = "v2"
        val periodisering = SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.nyPeriode(nyPeriode, "default-verdi") shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, nyPeriode),
        )
    }

    @Test
    fun `støtter nullable`() {
        val periode1 = Periode(mandagUke1, mandagUke2.minusDays(1))
        val hull = Periode(mandagUke2, mandagUke2)
        val periode2 = Periode(mandagUke2.plusDays(1), søndagUke2)
        val nyPeriode = Periode(mandagUke1.plusDays(1), søndagUke2.minusDays(1))
        val verdi1: String = "v1"
        val verdi2: String = "v2"
        val periodisering = SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi("default-verdi", hull),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.nyPeriode(nyPeriode, "default-verdi") shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, Periode(mandagUke1.plusDays(1), mandagUke2.minusDays(1))),
            PeriodeMedVerdi("default-verdi", hull),
            PeriodeMedVerdi(verdi2, Periode(mandagUke2.plusDays(1), søndagUke2.minusDays(1))),
        )
    }

    @Test
    fun `ingen overlapp`() {
        val originalPeriode = Periode(mandagUke1, søndagUke2)
        val verdi = "test"
        val periodisering = SammenhengendePeriodisering(verdi, originalPeriode)
        periodisering.nyPeriode(
            periode = originalPeriode.plus14Dager(),
            defaultVerdiDersomDenMangler = "default-verdi",
        ) shouldBe SammenhengendePeriodisering(
            "default-verdi",
            originalPeriode.plus14Dager(),
        )
    }

    @Test
    fun `1 dag overlapp`() {
        val originalPeriode = Periode(mandagUke1, mandagUke1)
        val verdi = "test"
        val periodisering = SammenhengendePeriodisering(verdi, originalPeriode)
        periodisering.nyPeriode(Periode(mandagUke1, søndagUke2), "default-verdi") shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi, Periode(mandagUke1, mandagUke1)),
            PeriodeMedVerdi("default-verdi", Periode(mandagUke1.plusDays(1), søndagUke2)),
        )
    }
}
