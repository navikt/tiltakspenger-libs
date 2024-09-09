package no.nav.tiltakspenger.libs.periodisering.periode

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.periodisering.Periode
import no.nav.tiltakspenger.libs.periodisering.inneholderOverlapp
import no.nav.tiltakspenger.libs.periodisering.juni
import no.nav.tiltakspenger.libs.periodisering.leggSammen
import no.nav.tiltakspenger.libs.periodisering.leggSammenMed
import no.nav.tiltakspenger.libs.periodisering.mai
import no.nav.tiltakspenger.libs.periodisering.overlappendePerioder
import no.nav.tiltakspenger.libs.periodisering.trekkFra
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodeTest {
    private val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 18.mai(2022))
    private val periode2 = Periode(fraOgMed = 17.mai(2022), tilOgMed = 21.mai(2022))
    private val periode3 = Periode(fraOgMed = 19.mai(2022), tilOgMed = 20.mai(2022))

    @Test
    fun inneholderHele() {
        assertTrue(periode1.inneholderHele(periode1))
        assertTrue(periode2.inneholderHele(periode3))
        assertFalse(periode1.inneholderHele(periode2))
    }

    @Test
    fun overlapperMed() {
        assertTrue(periode1.overlapperMed(periode1))
        assertTrue(periode1.overlapperMed(periode2))
        assertFalse(periode1.overlapperMed(periode3))
    }

    @Test
    fun intersect() {
        val fellesperiode = Periode(fraOgMed = 17.mai(2022), tilOgMed = 18.mai(2022))
        assertEquals(periode1, periode1.overlappendePeriode(periode1))
        assertEquals(fellesperiode, periode1.overlappendePeriode(periode2))
        assertEquals(fellesperiode, periode2.overlappendePeriode(periode1))
        assertNotEquals(fellesperiode, periode2.overlappendePeriode(periode2))
    }

    @Test
    fun overlapperIkkeMed() {
        val periodeSomIkkeOverlapper = Periode(fraOgMed = 13.mai(2022), tilOgMed = 16.mai(2022))
        assertEquals(periodeSomIkkeOverlapper, periode1.ikkeOverlappendePeriode(periode2).first())
    }

    @Test
    fun `to komplett overlappende perioder skal gi tomt svar`() {
        assertEquals(emptyList<Periode>(), periode1.ikkeOverlappendePeriode(periode1))
    }

    @Test
    fun `to overlappende perioder`() {
        val periodeEn = Periode(fraOgMed = 13.mai(2022), tilOgMed = 16.mai(2022))
        val periodeTo = Periode(fraOgMed = 13.mai(2022), tilOgMed = 15.mai(2022))
        val fasit = Periode(fraOgMed = 16.mai(2022), tilOgMed = 16.mai(2022))
        assertEquals(fasit, periodeEn.ikkeOverlappendePeriode(periodeTo).first())
        assertEquals(1, periodeEn.ikkeOverlappendePeriode(periodeTo).size)
    }

    @Test
    fun `ikkeOverlappendePerioder fjerner overlapp mellom flere perioder --fengsel---kvp---`() {
        val periodeEn = Periode(fraOgMed = 1.mai(2022), tilOgMed = 15.mai(2022))
        val fengselPeriode = Periode(fraOgMed = 5.mai(2022), tilOgMed = 6.mai(2022))
        val kvpPeriode = Periode(fraOgMed = 11.mai(2022), tilOgMed = 12.mai(2022))

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
                Periode(fraOgMed = 1.mai(2022), tilOgMed = 4.mai(2022)),
                Periode(fraOgMed = 7.mai(2022), tilOgMed = 10.mai(2022)),
                Periode(fraOgMed = 13.mai(2022), tilOgMed = 15.mai(2022)),
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
        val periodeEn = Periode(fraOgMed = 3.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 6.mai(2022), tilOgMed = 18.mai(2022))
        val perioder = periodeEn.trekkFra(listOf(periodeTo))
        assertEquals(1, perioder.size)
        assertEquals(3.mai(2022), perioder[0].fraOgMed)
        assertEquals(5.mai(2022), perioder[0].tilOgMed)
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
    fun `man kan ikke trekke flere overlappende perioder fra en annen periode`() {
        val periodeEn = Periode(fraOgMed = 3.mai(2022), tilOgMed = 15.mai(2022))
        val periodeTo = Periode(fraOgMed = 6.mai(2022), tilOgMed = 10.mai(2022))
        val periodeTre = Periode(fraOgMed = 9.mai(2022), tilOgMed = 12.mai(2022))
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            periodeEn.trekkFra(listOf(periodeTo, periodeTre))
        }
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
    fun `overlappende perioder skal fungere uansett hvilken liste som er subjekt og objekt`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 25.mai(2022))
        val periode2 = Periode(fraOgMed = 19.mai(2022), tilOgMed = 1.juni(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 23.mai(2022))
        val periode4 = Periode(fraOgMed = 24.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = listOf(periode1, periode2).overlappendePerioder(listOf(periode3, periode4))
        val overlappendePerioder2 = listOf(periode3, periode4).overlappendePerioder(listOf(periode1, periode2))

        overlappendePerioder.size shouldBe 1
        overlappendePerioder2.size shouldBe 1
        overlappendePerioder.first() shouldBe overlappendePerioder2.first()
    }

    @Test
    fun `perioder som er adjacent med periode skal ikke ha noen overlappende periode`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 21.mai(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 27.mai(2022))
        val periode4 = Periode(fraOgMed = 27.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = periode1.overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder.size shouldBe 0
    }

    @Test
    fun `perioder som er delvis overlappende med periode skal ha en overlappende periode`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 25.mai(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 23.mai(2022))
        val periode4 = Periode(fraOgMed = 24.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = periode1.overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder.size shouldBe 1
        overlappendePerioder.first() shouldBe Periode(fraOgMed = 22.mai(2022), tilOgMed = 25.mai(2022))
    }

    @Test
    fun `perioder som er fullstendig overlappende med periode skal ha en overlappende periode som er lik den minste perioden`() {
        val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 1.juni(2022))
        val periode3 = Periode(fraOgMed = 22.mai(2022), tilOgMed = 23.mai(2022))
        val periode4 = Periode(fraOgMed = 24.mai(2022), tilOgMed = 30.mai(2022))
        val overlappendePerioder = periode1.overlappendePerioder(listOf(periode3, periode4))

        overlappendePerioder.size shouldBe 1
        overlappendePerioder.first() shouldBe Periode(fraOgMed = 22.mai(2022), tilOgMed = 30.mai(2022))
    }

    @Test
    fun `Støtt Periode(MIN,MAX)`() {
        Periode(LocalDate.MIN, LocalDate.MAX)
    }
}
