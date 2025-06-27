package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodiseringKrympTest {
    private val start = LocalDate.of(2024, 1, 1)
    private val midt = LocalDate.of(2024, 6, 1)
    private val slutt = LocalDate.of(2024, 12, 31)

    @Test
    fun `ny periode lik original periode`() {
        val originalPeriode = Periode(start, slutt)
        val verdi = "test"
        val periodisering = SammenhengendePeriodisering(verdi, originalPeriode)
        periodisering.krymp(originalPeriode) shouldBe SammenhengendePeriodisering(verdi, originalPeriode)
    }

    @Test
    fun `krymping av av siste del av periode`() {
        val originalPeriode = Periode(start, slutt)
        val nyPeriode = Periode(midt, slutt)
        val verdi = "test"
        val periodisering = SammenhengendePeriodisering(verdi, originalPeriode)
        periodisering.krymp(nyPeriode) shouldBe SammenhengendePeriodisering(verdi, nyPeriode)
    }

    @Test
    fun `krymping av av første del av periode`() {
        val originalPeriode = Periode(start, slutt)
        val nyPeriode = Periode(start, midt)
        val verdi = "test"
        SammenhengendePeriodisering(
            verdi,
            originalPeriode,
        ).krymp(nyPeriode) shouldBe SammenhengendePeriodisering(verdi, nyPeriode)
    }

    @Test
    fun `fjerner perioden mellom midt og slutt`() {
        val originalPeriode = Periode(start, slutt)
        val nyPeriode = Periode(start.minusDays(1), midt)
        val verdi = "test"
        SammenhengendePeriodisering(verdi, originalPeriode).krymp(nyPeriode) shouldBe SammenhengendePeriodisering(
            verdi,
            Periode(start, midt),
        )
    }

    @Test
    fun `fjerner perioden mellom start og midt`() {
        val originalPeriode = Periode(start, slutt)
        val nyPeriode = Periode(midt, slutt.plusDays(1))
        val verdi = "test"
        SammenhengendePeriodisering(verdi, originalPeriode).krymp(nyPeriode) shouldBe SammenhengendePeriodisering(
            verdi,
            Periode(midt, slutt),
        )
    }

    @Test
    fun `periodene har ingen overlapp`() {
        val originalPeriode = Periode(start, midt)
        val nyPeriode = Periode(midt.plusDays(1), slutt)
        val verdi = "test"
        val periodisering = SammenhengendePeriodisering(verdi, originalPeriode)
        periodisering.krymp(nyPeriode) shouldBe TomPeriodisering.instance()
    }

    @Test
    fun `krymper deler av første periode`() {
        val periode1 = Periode(start, midt)
        val periode2 = Periode(midt.plusDays(1), slutt)
        val nyPeriode = Periode(midt, slutt)
        val verdi1 = "v1"
        val verdi2 = "v2"
        val periodisering = SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.krymp(nyPeriode) shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, Periode(midt, midt)),
            PeriodeMedVerdi(verdi2, periode2),
        )
    }

    @Test
    fun `fjerner periode pga krymping`() {
        val periode1 = Periode(start, midt)
        val periode2 = Periode(midt.plusDays(1), slutt)
        val nyPeriode = Periode(start.plusDays(1), midt.minusDays(1))
        val verdi1 = "v1"
        val verdi2 = "v2"
        val periodisering = SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.krymp(nyPeriode) shouldBe SammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, nyPeriode),
        )
    }

    @Test
    fun `støtter hull`() {
        val periode1 = Periode(start, midt.minusDays(1))
        val periode2 = Periode(midt.plusDays(1), slutt)
        val nyPeriode = Periode(start.plusDays(1), slutt.minusDays(1))
        val verdi1 = "v1"
        val verdi2 = "v2"
        val periodisering = IkkesammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.krymp(nyPeriode) shouldBe IkkesammenhengendePeriodisering(
            PeriodeMedVerdi(verdi1, Periode(start.plusDays(1), midt.minusDays(1))),
            PeriodeMedVerdi(verdi2, Periode(midt.plusDays(1), slutt.minusDays(1))),
        )
    }
}
