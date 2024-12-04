package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.periodisering.Periodisering.Companion.reduser
import org.junit.jupiter.api.Test

class PeriodiseringMedNullableTest {

    private val totalePeriode = Periode(fraOgMed = 13.mai(2022), tilOgMed = 21.mai(2022))
    private val periode1 = Periode(fraOgMed = 13.mai(2022), tilOgMed = 18.mai(2022))
    private val periode2 = Periode(fraOgMed = 17.mai(2022), tilOgMed = 21.mai(2022))
    private val periode3 = Periode(fraOgMed = 19.mai(2022), tilOgMed = 20.mai(2022))

    @Test
    fun `skal kunne sette null i periodene`() {
        val periodisering = Periodisering<Int?>(null, totalePeriode)
            .setVerdiForDelPeriode(1, periode1)
            .setVerdiForDelPeriode(null, periode2)
            .setVerdiForDelPeriode(null, periode3)

        periodisering.size shouldBe 2
    }

    @Test
    fun `skal kunne redusere flere perioder med null og ikke-null til en periode`() {
        val periodisering1 = Periodisering<Int?>(null, totalePeriode)
        val periodisering2 = Periodisering<Int?>(null, totalePeriode)
        val periodisering3 = Periodisering<Int?>(3, totalePeriode)
        val periodisering4 = Periodisering<Int?>(2, totalePeriode)

        val total = listOf(periodisering1, periodisering2, periodisering3, periodisering4).reduser { int1, int2 ->
            when {
                int1 == null && int2 == null -> null
                int1 == null -> int2
                int2 == null -> int1
                else -> int1 * int2
            }
        }

        total.size shouldBe 1
        total.first().verdi shouldBe 6
    }
}
