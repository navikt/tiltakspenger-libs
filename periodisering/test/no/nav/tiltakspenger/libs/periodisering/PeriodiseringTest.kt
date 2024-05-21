package no.nav.tiltakspenger.libs.periodisering

import org.junit.jupiter.api.Test

class PeriodiseringTest {

    private val totalePeriode = Periode(fra = 13.mai(2022), til = 21.mai(2022))
    private val delPeriode = Periode(fra = 18.mai(2022), til = 21.mai(2022))

    @Test
    fun `skal kunne sette samme verdi som er i perioden fra før`() {
        val periodisering = Periodisering(1, totalePeriode)
        periodisering.setVerdiForDelPeriode(1, delPeriode)
        // Skal ikke feile, noe det gjorde før..
    }
}
