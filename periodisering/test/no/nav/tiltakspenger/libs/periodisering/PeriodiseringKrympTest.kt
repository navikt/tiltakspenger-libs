package no.nav.tiltakspenger.libs.periodisering

import io.kotest.assertions.throwables.shouldThrow
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
        val periodisering = Periodisering(verdi, originalPeriode)
        periodisering.krymp(originalPeriode) shouldBe Periodisering(verdi, originalPeriode)
    }

    @Test
    fun `krymping av av siste del av periode`() {
        val originalPeriode = Periode(start, slutt)
        val nyPeriode = Periode(midt, slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        periodisering.krymp(nyPeriode) shouldBe Periodisering(verdi, nyPeriode)
    }

    @Test
    fun `krymping av av første del av periode`() {
        val originalPeriode = Periode(start, slutt)
        val nyPeriode = Periode(start, midt)
        val verdi = "test"
        Periodisering(verdi, originalPeriode).krymp(nyPeriode) shouldBe Periodisering(verdi, nyPeriode)
    }

    @Test
    fun `kan ikke utvide start`() {
        val originalPeriode = Periode(start, slutt)
        val nyPeriode = Periode(start.minusDays(1), midt)
        val verdi = "test"
        shouldThrow<IllegalArgumentException> {
            Periodisering(verdi, originalPeriode).krymp(nyPeriode)
        }.message shouldBe "Kan ikke krympe, ny periode 31. desember 2023 – 1. juni 2024 må ligge innenfor 1. januar – 31. desember 2024"
    }

    @Test
    fun `kan ikke utvide slutt`() {
        val originalPeriode = Periode(start, slutt)
        val nyPeriode = Periode(midt, slutt.plusDays(1))
        val verdi = "test"
        shouldThrow<IllegalArgumentException> {
            Periodisering(verdi, originalPeriode).krymp(nyPeriode)
        }.message shouldBe "Kan ikke krympe, ny periode 1. juni 2024 – 1. januar 2025 må ligge innenfor 1. januar – 31. desember 2024"
    }

    @Test
    fun `periodene har ingen overlapp`() {
        val originalPeriode = Periode(start, midt)
        val nyPeriode = Periode(midt.plusDays(1), slutt)
        val verdi = "test"
        val periodisering = Periodisering(verdi, originalPeriode)
        shouldThrow<IllegalArgumentException> {
            periodisering.krymp(nyPeriode)
        }.message shouldBe "Kan ikke krympe, ny periode 2. juni – 31. desember 2024 må ligge innenfor 1. januar – 1. juni 2024"
    }

    @Test
    fun `krymper deler av første periode`() {
        val periode1 = Periode(start, midt)
        val periode2 = Periode(midt.plusDays(1), slutt)
        val nyPeriode = Periode(midt, slutt)
        val verdi1 = "v1"
        val verdi2 = "v2"
        val periodisering = Periodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.krymp(nyPeriode) shouldBe Periodisering(
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
        val periodisering = Periodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.krymp(nyPeriode) shouldBe Periodisering(
            PeriodeMedVerdi(verdi1, nyPeriode),
        )
    }

    @Test
    fun `støtter nullable`() {
        val periode1 = Periode(start, midt.minusDays(1))
        val hull = Periode(midt, midt)
        val periode2 = Periode(midt.plusDays(1), slutt)
        val nyPeriode = Periode(start.plusDays(1), slutt.minusDays(1))
        val verdi1 = "v1"
        val verdi2 = "v2"
        val periodisering = Periodisering(
            PeriodeMedVerdi(verdi1, periode1),
            PeriodeMedVerdi(null, hull),
            PeriodeMedVerdi(verdi2, periode2),
        )
        periodisering.krymp(nyPeriode) shouldBe Periodisering(
            PeriodeMedVerdi(verdi1, Periode(start.plusDays(1), midt.minusDays(1))),
            PeriodeMedVerdi(null, hull),
            PeriodeMedVerdi(verdi2, Periode(midt.plusDays(1), slutt.minusDays(1))),
        )
    }
}
