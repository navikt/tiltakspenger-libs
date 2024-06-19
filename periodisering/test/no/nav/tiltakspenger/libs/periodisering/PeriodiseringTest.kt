package no.nav.tiltakspenger.libs.periodisering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodiseringTest {

    private val totalePeriode = Periode(fraOgMed = 13.mai(2022), tilOgMed = 21.mai(2022))
    private val delPeriode = Periode(fraOgMed = 18.mai(2022), tilOgMed = 21.mai(2022))

    @Test
    fun `skal kunne sette samme verdi som er i perioden fra før`() {
        val periodisering = Periodisering(1, totalePeriode)
        periodisering.setVerdiForDelPeriode(1, delPeriode)
        // Skal ikke feile, noe det gjorde før..
    }

    @Test
    fun `skal kunne opprette en tom periodisering`() {
        Periodisering(emptyList<PeriodeMedVerdi<String>>())
    }

    @Test
    fun `skal ikke kunne opprette en periodisering med hull`() {
        shouldThrow<IllegalArgumentException> {
            Periodisering(
                listOf(
                    PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
                    PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 1, 24))),
                ),
            )
        }
    }

    @Test
    fun `skal kunne opprette en periodisering med perioder i ikke-kronologisk rekkefølge`() {
        Periodisering(
            listOf(
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 1, 24))),
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 21))),
            ),
        )
    }

    @Test
    fun `to periodiseringer med like perioder i ulik rekkefølge skal være like`() {
        val periodisering1 = Periodisering(
            listOf(
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 1, 24))),
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 21))),
            ),
        )
        val periodisering2 = Periodisering(
            listOf(
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 21))),
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 1, 24))),
            ),
        )
        periodisering1 shouldBeEqual periodisering2
    }

    @Test
    fun `skal ikke kunne opprette en periodisering med duplikate perioder`() {
        shouldThrow<IllegalArgumentException> {
            Periodisering(
                listOf(
                    PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
                    PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
                ),
            )
        }
    }

    @Test
    fun `skal ikke kunne opprette en periodisering med overlappende perioder`() {
        shouldThrow<IllegalArgumentException> {
            Periodisering(
                listOf(
                    PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
                    PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 10), LocalDate.of(2023, 1, 30))),
                ),
            )
        }
    }
}
