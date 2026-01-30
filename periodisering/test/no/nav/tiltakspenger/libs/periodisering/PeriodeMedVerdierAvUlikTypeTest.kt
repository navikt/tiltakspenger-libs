package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.oktober
import no.nav.tiltakspenger.libs.periode.Periode
import org.junit.jupiter.api.Test

class PeriodeMedVerdierAvUlikTypeTest {
    data class DagsatsOgAntallBarn(
        val dagsats: Long,
        val antallBarn: Int,
    ) {
        companion object {
            fun kombinerDagsatsOgAntallBarn(dagsats: Long, antallBarn: Int): DagsatsOgAntallBarn =
                DagsatsOgAntallBarn(dagsats, antallBarn)

            fun trekkUtDagsats(dagsatsOgAntallBarn: DagsatsOgAntallBarn, periode: Periode): Long = dagsatsOgAntallBarn.dagsats
            fun trekkUtAntallBarn(dagsatsOgAntallBarn: DagsatsOgAntallBarn, periode: Periode): Int = dagsatsOgAntallBarn.antallBarn
        }
    }

    @Test
    fun `erstatter man alle dager i perioden med samme nye verdi vil den resulterende perioden være homogen og bare ha én subperiode`() {
        val perioderMedDagsats =
            SammenhengendePeriodisering(255L, Periode(1.oktober(2023), 10.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(1.oktober(2023), 1.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(2.oktober(2023), 2.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(3.oktober(2023), 3.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(4.oktober(2023), 4.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(5.oktober(2023), 5.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(6.oktober(2023), 6.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(7.oktober(2023), 7.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(8.oktober(2023), 8.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(9.oktober(2023), 9.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(10.oktober(2023), 10.oktober(2023)))
        perioderMedDagsats.size shouldBe 1
    }

    @Test
    fun `erstatter man alle dager i perioden nye, unike verdier vil den resulterende perioden være en subperiode per dag`() {
        val perioderMedDagsats =
            SammenhengendePeriodisering(255L, Periode(1.oktober(2023), 10.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(1.oktober(2023), 1.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(2.oktober(2023), 2.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(3.oktober(2023), 3.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(4.oktober(2023), 4.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(5.oktober(2023), 5.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(6.oktober(2023), 6.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(7.oktober(2023), 7.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(8.oktober(2023), 8.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(9.oktober(2023), 9.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(10.oktober(2023), 10.oktober(2023)))
        perioderMedDagsats.size shouldBe 10
    }

    @Test
    fun `subperioder med samme verdi skal slås sammen hvis de ligger inntil hverandre`() {
        val perioderMedDagsats =
            SammenhengendePeriodisering(255L, Periode(1.oktober(2023), 10.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(1.oktober(2023), 1.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(2.oktober(2023), 2.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(3.oktober(2023), 3.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(4.oktober(2023), 4.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(5.oktober(2023), 5.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(6.oktober(2023), 6.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(7.oktober(2023), 7.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(8.oktober(2023), 8.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(9.oktober(2023), 9.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(10.oktober(2023), 10.oktober(2023)))
        perioderMedDagsats.size shouldBe 5
        perioderMedDagsats.count { it.verdi == 300L } shouldBe 3
        perioderMedDagsats.count { it.verdi == 301L } shouldBe 2
        perioderMedDagsats.count { it.verdi == 255L } shouldBe 0
    }

    @Test
    fun `en periode som er en kombinasjon av to verdier kan splittes opp igjen i de enkeltstående verdiene`() {
        fun kontrollerDagsats(perioderMedDagsats: Periodisering<Long>) {
            perioderMedDagsats.size shouldBe 2
            perioderMedDagsats.count { it.verdi == 300L } shouldBe 1
            perioderMedDagsats.count { it.verdi == 301L } shouldBe 1
            perioderMedDagsats.count { it.verdi == 255L } shouldBe 0
        }

        fun kontrollerAntallBarn(perioderMedAntallBarn: Periodisering<Int>) {
            perioderMedAntallBarn.size shouldBe 3
            perioderMedAntallBarn.count { it.verdi == 1 } shouldBe 2
            perioderMedAntallBarn.count { it.verdi == 2 } shouldBe 1
            perioderMedAntallBarn.count { it.verdi == 0 } shouldBe 0
        }

        val perioderMedDagsats =
            SammenhengendePeriodisering(255L, Periode(1.oktober(2023), 4.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(1.oktober(2023), 1.oktober(2023)))
                .setVerdiForDelperiode(300L, Periode(2.oktober(2023), 2.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(3.oktober(2023), 3.oktober(2023)))
                .setVerdiForDelperiode(301L, Periode(4.oktober(2023), 4.oktober(2023)))
        kontrollerDagsats(perioderMedDagsats)

        val perioderMedAntallBarn =
            SammenhengendePeriodisering(0, Periode(1.oktober(2023), 4.oktober(2023)))
                .setVerdiForDelperiode(1, Periode(1.oktober(2023), 1.oktober(2023)))
                .setVerdiForDelperiode(2, Periode(2.oktober(2023), 2.oktober(2023)))
                .setVerdiForDelperiode(2, Periode(3.oktober(2023), 3.oktober(2023)))
                .setVerdiForDelperiode(1, Periode(4.oktober(2023), 4.oktober(2023)))
        kontrollerAntallBarn(perioderMedAntallBarn)

        val perioderMedDagsatsOgAntallBarn =
            perioderMedDagsats.kombiner(perioderMedAntallBarn, DagsatsOgAntallBarn::kombinerDagsatsOgAntallBarn)
        perioderMedDagsatsOgAntallBarn.size shouldBe 4

        kontrollerDagsats(perioderMedDagsatsOgAntallBarn.map(DagsatsOgAntallBarn::trekkUtDagsats))
        kontrollerAntallBarn(perioderMedDagsatsOgAntallBarn.map(DagsatsOgAntallBarn::trekkUtAntallBarn))
    }
}
