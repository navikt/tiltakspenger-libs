package no.nav.tiltakspenger.libs.periodisering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.dato.mai
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodiseringTest {

    private val totalePeriode = Periode(fraOgMed = 13.mai(2022), tilOgMed = 21.mai(2022))
    private val delPeriode = Periode(fraOgMed = 18.mai(2022), tilOgMed = 21.mai(2022))

    @Test
    fun `skal kunne sette samme verdi som er i perioden fra før`() {
        val periodisering = SammenhengendePeriodisering(1, totalePeriode)
        periodisering.setVerdiForDelperiode(1, delPeriode) shouldBe periodisering
    }

    @Test
    fun `skal ikke kunne opprette en sammenhengende periodisering med hull`() {
        shouldThrow<IllegalArgumentException> {
            SammenhengendePeriodisering(
                PeriodeMedVerdi("foo", 1 til 20.januar(2023)),
                PeriodeMedVerdi("bar", 22 til 24.januar(2023)),
            )
        }.message shouldBe "Ugyldig sammenhengende periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte dagen etter periode n slutter. Perioder: [1.–20. januar 2023, 22.–24. januar 2023]"
    }

    @Test
    fun `skal ikke kunne opprette en ikke-sammenhengende periodisering uten hull`() {
        shouldThrow<IllegalArgumentException> {
            IkkesammenhengendePeriodisering(
                PeriodeMedVerdi("foo", 1 til 20.januar(2023)),
                PeriodeMedVerdi("bar", 21 til 24.januar(2023)),
            )
        }.message shouldBe "En ikke-sammenhengendeperiode kan ikke være sammenhengende. Bruk [Periodisering] eller [SammenhengendePeriodisering] istedenfor. Perioder: [1.–20. januar 2023, 21.–24. januar 2023]"
    }

    @Test
    fun `skal ikke kunne opprette en sammenhengende periodisering med perioder i ikke-kronologisk rekkefølge`() {
        shouldThrow<IllegalArgumentException> {
            SammenhengendePeriodisering(
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 1, 24))),
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 21))),
            )
        }.message shouldBe "Ugyldig sammenhengende periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte dagen etter periode n slutter. Perioder: [22.–24. januar 2023, 1.–21. januar 2023]"
    }

    @Test
    fun `skal ikke kunne opprette en ikke-sammenhengende periodisering med perioder i ikke-kronologisk rekkefølge`() {
        shouldThrow<IllegalArgumentException> {
            IkkesammenhengendePeriodisering(
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 1, 24))),
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
            )
        }.message shouldBe "Ugyldig ikke-sammenhengende periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte etter periode n slutter. Perioder: [22.–24. januar 2023, 1.–20. januar 2023]"
    }

    @Test
    fun `to periodiseringer med like perioder skal være like`() {
        val periodisering1 = SammenhengendePeriodisering(
            PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 21))),
            PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 1, 24))),
        )
        val periodisering2 = SammenhengendePeriodisering(
            PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 21))),
            PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 22), LocalDate.of(2023, 1, 24))),
        )
        periodisering1 shouldBeEqual periodisering2
    }

    @Test
    fun `skal ikke kunne opprette en periodisering med duplikate perioder`() {
        shouldThrow<IllegalArgumentException> {
            SammenhengendePeriodisering(
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
            )
        }.message shouldBe "Ugyldig sammenhengende periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte dagen etter periode n slutter. Perioder: [1.–20. januar 2023, 1.–20. januar 2023]"
        shouldThrow<IllegalArgumentException> {
            IkkesammenhengendePeriodisering(
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
            )
        }.message shouldBe "Ugyldig ikke-sammenhengende periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte etter periode n slutter. Perioder: [1.–20. januar 2023, 1.–20. januar 2023]"
    }

    @Test
    fun `skal ikke kunne opprette en periodisering med overlappende perioder`() {
        shouldThrow<IllegalArgumentException> {
            SammenhengendePeriodisering(
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 10), LocalDate.of(2023, 1, 30))),
            )
        }
        shouldThrow<IllegalArgumentException> {
            IkkesammenhengendePeriodisering(
                PeriodeMedVerdi("foo", Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 20))),
                PeriodeMedVerdi("bar", Periode(LocalDate.of(2023, 1, 10), LocalDate.of(2023, 1, 30))),
            )
        }
    }
}
