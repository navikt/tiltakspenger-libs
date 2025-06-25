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
import no.nav.tiltakspenger.libs.periodisering.inneholderOverlapp
import no.nav.tiltakspenger.libs.periodisering.leggSammen
import no.nav.tiltakspenger.libs.periodisering.leggSammenMed
import no.nav.tiltakspenger.libs.periodisering.overlappendePerioder
import no.nav.tiltakspenger.libs.periodisering.til
import no.nav.tiltakspenger.libs.periodisering.trekkFra
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun overlapperMed() {
        (2 til 2.mai(2022)).overlapperMed((2 til 2.mai(2022))).shouldBeTrue()
        (1 til 1.mai(2022)).overlapperMed((2 til 2.mai(2022))).shouldBeFalse()
        (2 til 2.mai(2022)).overlapperMed((1 til 1.mai(2022))).shouldBeFalse()
        (1 til 3.mai(2022)).overlapperMed((2 til 2.mai(2022))).shouldBeTrue()
        (2 til 2.mai(2022)).overlapperMed((1 til 3.mai(2022))).shouldBeTrue()
        (1 til 3.mai(2022)).overlapperMed((3 til 4.mai(2022))).shouldBeTrue()
        (3 til 4.mai(2022)).overlapperMed((1 til 3.mai(2022))).shouldBeTrue()
        assertTrue(periode1.overlapperMed(periode1))
        assertTrue(periode1.overlapperMed(periode2))
        assertFalse(periode1.overlapperMed(periode3))

        (LocalDate.MIN til LocalDate.MAX).overlapperMed(1 til 2.mai(2022)).shouldBeTrue()
        (LocalDate.MIN til LocalDate.MAX).overlapperMed((LocalDate.MIN til LocalDate.MAX)).shouldBeTrue()
        ((2.mai(2022)) til LocalDate.MAX).overlapperMed((LocalDate.MIN til 1.mai(2022))).shouldBeFalse()
        ((1.mai(2022)) til LocalDate.MAX).overlapperMed((LocalDate.MIN til 1.mai(2022))).shouldBeTrue()
    }

    @Test
    fun intersect() {
        val fellesperiode = 17 til 18.mai(2022)
        assertEquals(periode1, periode1.overlappendePeriode(periode1))
        assertEquals(fellesperiode, periode1.overlappendePeriode(periode2))
        assertEquals(fellesperiode, periode2.overlappendePeriode(periode1))
        assertNotEquals(fellesperiode, periode2.overlappendePeriode(periode2))
        (LocalDate.MIN til LocalDate.MAX).overlappendePeriode((LocalDate.MIN til LocalDate.MAX)) shouldBe
            (LocalDate.MIN til LocalDate.MAX)
    }

    @Test
    fun overlapperIkkeMed() {
        val periodeSomIkkeOverlapper = 13 til 16.mai(2022)
        assertEquals(periodeSomIkkeOverlapper, periode1.ikkeOverlappendePeriode(periode2).first())
        (LocalDate.MIN til LocalDate.MAX).ikkeOverlappendePeriode((LocalDate.MIN til LocalDate.MAX)) shouldBe emptyList()
    }

    @Test
    fun `to komplett overlappende perioder skal gi tomt svar`() {
        assertEquals(emptyList<Periode>(), periode1.ikkeOverlappendePeriode(periode1))
    }

    @Test
    fun `to overlappende perioder`() {
        val periodeEn = 13 til 16.mai(2022)
        val periodeTo = 13 til 15.mai(2022)
        val fasit = 16 til 16.mai(2022)
        assertEquals(fasit, periodeEn.ikkeOverlappendePeriode(periodeTo).first())
        assertEquals(1, periodeEn.ikkeOverlappendePeriode(periodeTo).size)
    }

    @Test
    fun `ikkeOverlappendePerioder fjerner overlapp mellom flere perioder --fengsel---kvp---`() {
        val periodeEn = 1 til 15.mai(2022)
        val fengselPeriode = 5 til 6.mai(2022)
        val kvpPeriode = 11 til 12.mai(2022)

        val result =
            periodeEn.ikkeOverlappendePerioder(
                listOf(
                    fengselPeriode,
                    kvpPeriode,
                ),
            )
        assertEquals(3, result.size)
        assertEquals(
            listOf(
                1 til 4.mai(2022),
                7 til 10.mai(2022),
                13 til 15.mai(2022),
            ),
            result,
        )
    }

    @Test
    fun `ikkeOverlappendePerioder fjerner overlapp mellom flere perioder --fengselOgKvp---`() {
        val periodeEn = Periode(fraOgMed = 1.mai(2022), tilOgMed = 15.mai(2022))
        val fengselPeriode = Periode(fraOgMed = 5.mai(2022), tilOgMed = 11.mai(2022))
        val kvpPeriode = Periode(fraOgMed = 10.mai(2022), tilOgMed = 12.mai(2022))

        val result =
            periodeEn.ikkeOverlappendePerioder(
                listOf(
                    fengselPeriode,
                    kvpPeriode,
                ),
            )
        assertEquals(2, result.size)
        assertEquals(
            listOf(
                Periode(fraOgMed = 1.mai(2022), tilOgMed = 4.mai(2022)),
                Periode(fraOgMed = 13.mai(2022), tilOgMed = 15.mai(2022)),
            ),
            result,
        )
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
    fun `to overlappende perioder gir overlapp`() {
        val periodeEn = Periode(fraOgMed = 6.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 1.mai(2022), tilOgMed = 7.mai(2022))
        assertEquals(true, listOf(periodeEn, periodeTo).inneholderOverlapp())
    }

    @Test
    fun `to ikke-overlappende perioder gir ikke overlapp`() {
        val periodeEn = Periode(fraOgMed = 8.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 1.mai(2022), tilOgMed = 7.mai(2022))
        assertEquals(false, listOf(periodeEn, periodeTo).inneholderOverlapp())
    }

    @Test
    fun `tre ikke-overlappende perioder der den tredje er mellom de to første gir ikke overlapp`() {
        val periodeEn = Periode(fraOgMed = 1.mai(2022), tilOgMed = 10.mai(2022))
        val periodeTo = Periode(fraOgMed = 20.mai(2022), tilOgMed = 25.mai(2022))
        val periodeTre = Periode(fraOgMed = 11.mai(2022), tilOgMed = 19.mai(2022))
        assertEquals(false, listOf(periodeEn, periodeTo, periodeTre).inneholderOverlapp())
    }

    @Test
    fun `tre ikke-overlappende perioder der den tredje med den andre gir overlapp`() {
        val periodeEn = Periode(fraOgMed = 1.mai(2022), tilOgMed = 10.mai(2022))
        val periodeTo = Periode(fraOgMed = 20.mai(2022), tilOgMed = 25.mai(2022))
        val periodeTre = Periode(fraOgMed = 11.mai(2022), tilOgMed = 20.mai(2022))
        assertEquals(true, listOf(periodeEn, periodeTo, periodeTre).inneholderOverlapp())
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
    fun `perioder som er adjacent skal ikke ha noen overlappende periode`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 18.mai(2022))
        val periode2 = Periode(fraOgMed = 19.mai(2022), tilOgMed = 21.mai(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 27.mai(2022))
        val periode4 = Periode(fraOgMed = 27.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = listOf(periode1, periode2).overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder.size shouldBe 0
    }

    @Test
    fun `perioder som er delvis overlappende skal ha en overlappende periode`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 18.mai(2022))
        val periode2 = Periode(fraOgMed = 19.mai(2022), tilOgMed = 25.mai(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 23.mai(2022))
        val periode4 = Periode(fraOgMed = 24.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = listOf(periode1, periode2).overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder.size shouldBe 1
        overlappendePerioder.first() shouldBe Periode(fraOgMed = 22.mai(2022), tilOgMed = 25.mai(2022))
    }

    @Test
    fun `perioder som er fullstendig overlappende skal ha en overlappende periode som er lik den minste perioden`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 25.mai(2022))
        val periode2 = Periode(fraOgMed = 19.mai(2022), tilOgMed = 1.juni(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 23.mai(2022))
        val periode4 = Periode(fraOgMed = 24.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = listOf(periode1, periode2).overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder.size shouldBe 1
        overlappendePerioder.first() shouldBe Periode(fraOgMed = 22.mai(2022), tilOgMed = 30.mai(2022))
    }

    @Test
    fun `overlappendePerioder skal fungere uansett hvilken liste som er subjekt og objekt`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 25.mai(2022))
        val periode2 = Periode(fraOgMed = 19.mai(2022), tilOgMed = 1.juni(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 23.mai(2022))
        val periode4 = Periode(fraOgMed = 24.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = listOf(periode1, periode2).overlappendePerioder(listOf(periode3, periode4))
        val overlappendePerioder2 = listOf(periode3, periode4).overlappendePerioder(listOf(periode1, periode2))

        val exptected = listOf(Periode(fraOgMed = 22.mai(2022), tilOgMed = 30.mai(2022)))
        overlappendePerioder shouldBe exptected
        overlappendePerioder2 shouldBe exptected
    }

    @Test
    fun `overlappendePerioder skal kunne splitte den originale perioden`() {
        val originalPeriode = 1 til 31.januar(2022)
        val overlappende1 = 1 til 1.januar(2022)
        val overlappende2 = 31 til 31.januar(2022)
        originalPeriode.overlappendePerioder(listOf(overlappende1, overlappende2)) shouldBe listOf(
            overlappende1,
            overlappende2,
        )
    }

    @Test
    fun `overlappendePerioder perfekt overlappende perioder skal slås sammen`() {
        val originalPeriode = 1 til 31.januar(2022)
        val overlappende1 = 1 til 15.januar(2022)
        val overlappende2 = 16 til 31.januar(2022)
        originalPeriode.overlappendePerioder(listOf(overlappende1, overlappende2)) shouldBe listOf(originalPeriode)
    }

    @Test
    fun `overlappendePerioder godtar overlapp`() {
        val originalPeriode = 1 til 31.januar(2022)
        val overlappende1 = 1 til 15.januar(2022)
        val overlappende2 = 15 til 31.januar(2022)
        originalPeriode.overlappendePerioder(listOf(overlappende1, overlappende2)) shouldBe listOf(originalPeriode)
    }

    @Test
    fun `overlappendePerioder skal gi tom liste`() {
        (1 til 31.januar(2022)).overlappendePerioder(emptyList()) shouldBe emptyList()
        (1 til 30.januar(2022)).overlappendePerioder(listOf(31 til 31.januar(2022))) shouldBe emptyList()
    }

    @Test
    fun `perioder som er adjacent med periode skal ikke ha noen overlappende periode`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 21.mai(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 27.mai(2022))
        val periode4 = Periode(fraOgMed = 27.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = periode1.overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder shouldBe emptyList()
    }

    @Test
    fun `perioder som er delvis overlappende med periode skal ha en overlappende periode`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 25.mai(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 23.mai(2022))
        val periode4 = Periode(fraOgMed = 24.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = periode1.overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder shouldBe listOf(Periode(fraOgMed = 22.mai(2022), tilOgMed = 25.mai(2022)))
    }

    @Test
    fun `perioder som er fullstendig overlappende med periode skal ha en overlappende periode som er lik den minste perioden`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 1.juni(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 23.mai(2022))
        val periode4 = Periode(fraOgMed = 24.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = periode1.overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder shouldBe listOf(Periode(fraOgMed = 22.mai(2022), tilOgMed = 30.mai(2022)))
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
}
