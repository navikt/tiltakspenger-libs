package no.nav.tiltakspenger.libs.periode

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.august
import no.nav.tiltakspenger.libs.dato.desember
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.dato.juli
import no.nav.tiltakspenger.libs.dato.juni
import no.nav.tiltakspenger.libs.dato.mai
import no.nav.tiltakspenger.libs.dato.september
import no.nav.tiltakspenger.libs.periodisering.Periode
import no.nav.tiltakspenger.libs.periodisering.leggSammen
import no.nav.tiltakspenger.libs.periodisering.leggSammenMed
import no.nav.tiltakspenger.libs.periodisering.til
import no.nav.tiltakspenger.libs.periodisering.trekkFra
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodeTest {
    private val periode1 = 13 til 18.mai(2022)
    private val periode2 = 17 til 21.mai(2022)
    private val periode3 = 19 til 20.mai(2022)

    @Test
    fun inneholderHele() {
        (2 til 2.mai(2022)).inneholderHele((2 til 2.mai(2022))).shouldBeTrue()
        (1 til 1.mai(2022)).inneholderHele((2 til 2.mai(2022))).shouldBeFalse()
        (1 til 2.mai(2022)).inneholderHele((2 til 2.mai(2022))).shouldBeTrue()
        (1 til 2.mai(2022)).inneholderHele((1 til 1.mai(2022))).shouldBeTrue()
        (1 til 3.mai(2022)).inneholderHele((2 til 2.mai(2022))).shouldBeTrue()
        (1 til 2.mai(2022)).inneholderHele((1 til 3.mai(2022))).shouldBeFalse()
        assertTrue(periode1.inneholderHele(periode1))
        assertTrue(periode2.inneholderHele(periode3))
        assertFalse(periode1.inneholderHele(periode2))

        (LocalDate.MIN til LocalDate.MAX).inneholderHele(1 til 2.mai(2022)).shouldBeTrue()
        (LocalDate.MIN til LocalDate.MAX).inneholderHele((LocalDate.MIN til LocalDate.MAX)).shouldBeTrue()
        ((2.mai(2022)) til LocalDate.MAX).inneholderHele((LocalDate.MIN til 1.mai(2022))).shouldBeFalse()
        ((1.mai(2022)) til LocalDate.MAX).inneholderHele((LocalDate.MIN til 1.mai(2022))).shouldBeFalse()
    }

    @Test
    fun `man kan trekke en periode fra en annen periode`() {
        val periodeEn = Periode(fraOgMed = 3.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 6.mai(2022), tilOgMed = 12.mai(2022))
        val perioder = periodeEn.trekkFra(listOf(periodeTo))
        assertEquals(2, perioder.size)
        assertEquals(3.mai(2022), perioder[0].fraOgMed)
        assertEquals(5.mai(2022), perioder[0].tilOgMed)
        assertEquals(13.mai(2022), perioder[1].fraOgMed)
        assertEquals(15.mai(2022), perioder[1].tilOgMed)
    }

    @Test
    fun `man kan trekke en periode fra en annen ikke-overlappende periode`() {
        val periodeEn = 3 til 15.mai(2022)
        val periodeTo = 6 til 18.mai(2022)
        periodeEn.trekkFra(listOf(periodeTo)) shouldBe listOf(3 til 5.mai(2022))
    }

    @Test
    fun `man kan trekke flere perioder fra en annen periode`() {
        val periodeEn = Periode(fraOgMed = 3.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 6.mai(2022), tilOgMed = 8.mai(2022))
        val periodeTre = Periode(fraOgMed = 10.mai(2022), tilOgMed = 12.mai(2022))
        val perioder = periodeEn.trekkFra(listOf(periodeTo, periodeTre))
        assertEquals(3, perioder.size)
        assertEquals(3.mai(2022), perioder[0].fraOgMed)
        assertEquals(5.mai(2022), perioder[0].tilOgMed)
        assertEquals(9.mai(2022), perioder[1].fraOgMed)
        assertEquals(9.mai(2022), perioder[1].tilOgMed)
        assertEquals(13.mai(2022), perioder[2].fraOgMed)
        assertEquals(15.mai(2022), perioder[2].tilOgMed)
    }

    @Test
    fun `man kan trekke flere connected perioder fra en annen periode`() {
        val periodeEn = Periode(fraOgMed = 3.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 6.mai(2022), tilOgMed = 9.mai(2022))
        val periodeTre = Periode(fraOgMed = 10.mai(2022), tilOgMed = 12.mai(2022))
        val perioder = periodeEn.trekkFra(listOf(periodeTo, periodeTre))
        assertEquals(2, perioder.size)
        assertEquals(3.mai(2022), perioder[0].fraOgMed)
        assertEquals(5.mai(2022), perioder[0].tilOgMed)
        assertEquals(13.mai(2022), perioder[1].fraOgMed)
        assertEquals(15.mai(2022), perioder[1].tilOgMed)
    }

    @Test
    fun `man kan trekke flere overlappende perioder fra en annen periode`() {
        val periodeEn = Periode(fraOgMed = 3.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 6.mai(2022), tilOgMed = 10.mai(2022))
        val periodeTre = Periode(fraOgMed = 9.mai(2022), tilOgMed = 12.mai(2022))
        periodeEn.trekkFra(listOf(periodeTo, periodeTre)) shouldBe
            listOf(
                Periode(fraOgMed = 3.mai(2022), tilOgMed = 5.mai(2022)),
                Periode(fraOgMed = 13.mai(2022), tilOgMed = 15.mai(2022)),
            )
    }

    @Test
    fun `Dersom man trekker fra en periode som er lik eller større får man en tom liste`() {
        val periodeEn = 3 til 15.mai(2022)
        val periodeTo = 2 til 15.mai(2022)
        val periodeTre = 3 til 16.mai(2022)
        val periodeFire = 2 til 16.mai(2022)
        periodeEn.trekkFra(periodeEn) shouldBe emptyList()
        periodeEn.trekkFra(periodeTo) shouldBe emptyList()
        periodeEn.trekkFra(periodeTre) shouldBe emptyList()
        periodeEn.trekkFra(periodeFire) shouldBe emptyList()
    }

    @Test
    fun `hvis man legger sammen to perioder som overlapper får man en periode`() {
        val periodeEn = Periode(fraOgMed = 3.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 10.mai(2022), tilOgMed = 20.mai(2022))
        val periodeTre = Periode(fraOgMed = 3.mai(2022), tilOgMed = 20.mai(2022))
        assertEquals(periodeTre, listOf(periodeEn, periodeTo).leggSammen().first())
    }

    @Test
    fun `hvis man legger sammen to tilstøtende perioder får man en periode`() {
        val periodeEn = Periode(fraOgMed = 6.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 1.mai(2022), tilOgMed = 5.mai(2022))
        val periodeTre = Periode(fraOgMed = 1.mai(2022), tilOgMed = 15.mai(2022))
        assertEquals(1, listOf(periodeEn, periodeTo).leggSammen().size)
        assertEquals(periodeTre, listOf(periodeEn, periodeTo).leggSammen().first())
    }

    @Test
    fun `hvis man legger sammen to tilstøtende perioder og en tredje separat periode får man to perioder`() {
        val periodeEn = Periode(fraOgMed = 6.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 1.mai(2022), tilOgMed = 5.mai(2022))
        val periodeFasitEn = Periode(fraOgMed = 1.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTre = Periode(fraOgMed = 18.mai(2022), tilOgMed = 20.mai(2022))
        val periodeFasitTo = Periode(fraOgMed = 18.mai(2022), tilOgMed = 20.mai(2022))
        assertEquals(periodeFasitEn, listOf(periodeEn, periodeTo, periodeTre).leggSammen().first())
        assertEquals(periodeFasitTo, listOf(periodeEn, periodeTo, periodeTre).leggSammen().last())
    }

    @Test
    fun testTrekkFraPerioder() {
        val perioder1 = listOf(Periode(LocalDate.of(2020, 10, 1), LocalDate.of(2023, 10, 10)))
        val perioder2 = listOf(Periode(LocalDate.of(2020, 10, 1), LocalDate.of(2023, 10, 10)))
        val tomPeriode = perioder1.trekkFra(perioder2)
        tomPeriode.size shouldBe 0
    }

    @Test
    fun `perioder som er adjacent skal legges sammen og bli en periode`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 18.mai(2022))
        val periode2 = Periode(fraOgMed = 19.mai(2022), tilOgMed = 21.mai(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 27.mai(2022))
        val periode4 = Periode(fraOgMed = 27.mai(2022), tilOgMed = 30.mai(2022))
        val nyePerioder = listOf(periode1, periode2).leggSammenMed(listOf(periode3, periode4))

        nyePerioder.size shouldBe 1
        nyePerioder.first() shouldBe Periode(fraOgMed = 13.mai(2022), tilOgMed = 30.mai(2022))
    }

    @Test
    fun `Støtt Periode(MIN,MAX)`() {
        LocalDate.MIN til (1.mai(2021))
        (1.mai(2021)) til LocalDate.MAX
        LocalDate.MIN til LocalDate.MAX
    }

    @Test
    fun `test til`() {
        13.mai(2022) til 18.mai(2022) shouldBe Periode(LocalDate.of(2022, 5, 13), LocalDate.of(2022, 5, 18))
        13.mai(2022) til 18.juni(2022) shouldBe Periode(LocalDate.of(2022, 5, 13), LocalDate.of(2022, 6, 18))
        1 til 31.juli(2022) shouldBe Periode(LocalDate.of(2022, 7, 1), LocalDate.of(2022, 7, 31))
        1 til 1.juli(2022) shouldBe Periode(LocalDate.of(2022, 7, 1), LocalDate.of(2022, 7, 1))
    }

    @Test
    fun `toString formaterer riktig`() {
        (1 til 1.juli(2022)).toString() shouldBe "1. juli 2022"
        (1 til 2.juli(2022)).toString() shouldBe "1.–2. juli 2022"
        (1.juli(2022) til 2.august(2022)).toString() shouldBe "1. juli – 2. august 2022"
        (1.juli(2022) til 1.august(2022)).toString() shouldBe "1. juli – 1. august 2022"
        (1.juli(2022) til 31.august(2022)).toString() shouldBe "1. juli – 31. august 2022"
        (1.juli(2022) til 30.september(2022)).toString() shouldBe "1. juli – 30. september 2022"
        (1.juli(2022) til 31.desember(2022)).toString() shouldBe "1. juli – 31. desember 2022"
        (LocalDate.MIN til (1.mai(2021))).toString() shouldBe "LocalDate.MIN – 1. mai 2021"
        (1.mai(2021) til LocalDate.MAX).toString() shouldBe "1. mai 2021 – LocalDate.MAX"
        (LocalDate.MIN til LocalDate.MAX).toString() shouldBe "LocalDate.MIN – LocalDate.MAX"
    }

    @Nested
    inner class FraOgMedTil {
        @Test
        fun `fraOgMedTil returnerer periode når fraOgMed er etter dato`() {
            val periode = 2.uke(2026)
            val resultat = periode.fraOgMedTil(15.januar(2026))
            resultat shouldBe Periode(5.januar(2026), 15.januar(2026))
        }

        @Test
        fun `fraOgMedTil returnerer null når fraOgMed er samme dag som dato`() {
            val periode = 2.uke(2026)
            periode.fraOgMedTil(5.januar(2026)) shouldBe null
        }

        @Test
        fun `fraOgMedTil returnerer null når fraOgMed er før dato`() {
            val periode = 2.uke(2026)
            periode.fraOgMedTil(4.januar(2026)) shouldBe null
        }
    }

    @Nested
    inner class TilOgMedTil {
        @Test
        fun `tilOgMedTil returnerer periode når tilOgMed er etter dato`() {
            val periode = 2.uke(2026)
            val resultat = periode.tilOgMedTil(15.januar(2026))
            resultat shouldBe Periode(11.januar(2026), 15.januar(2026))
        }

        @Test
        fun `tilOgMedTil returnerer null når tilOgMed er samme dag som dato`() {
            val periode = 2.uke(2026)
            periode.tilOgMedTil(11.januar(2026)) shouldBe null
        }

        @Test
        fun `tilOgMedTil returnerer null når tilOgMed er før dato`() {
            val periode = 2.uke(2026)
            periode.tilOgMedTil(10.januar(2026)) shouldBe null
        }
    }
}
