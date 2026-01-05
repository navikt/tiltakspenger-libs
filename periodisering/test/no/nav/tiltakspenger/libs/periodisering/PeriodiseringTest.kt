package no.nav.tiltakspenger.libs.periodisering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.dato.mai
import no.nav.tiltakspenger.libs.periode.uke
import org.junit.jupiter.api.Nested
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

    @Nested
    inner class Utvid {
        @Test
        fun `utvider sammenhengendeperiodisering med periode før`() {
            val periodisering = SammenhengendePeriodisering(PeriodeMedVerdi("A", 2.uke(2026)))
            val utvidet = periodisering.utvid("B", 1.uke(2026)..2.uke(2026))

            utvidet.shouldBeInstanceOf<SammenhengendePeriodisering<String>>()
            utvidet.size shouldBe 2
            utvidet.first().periode shouldBe 1.uke(2026)
            utvidet.first().verdi shouldBe "B"
            utvidet.last().periode shouldBe 2.uke(2026)
            utvidet.last().verdi shouldBe "A"
        }

        @Test
        fun `utvider sammenhengendeperiodisering med periode etter`() {
            val periodisering = SammenhengendePeriodisering(PeriodeMedVerdi("A", 2.uke(2026)))
            val utvidet = periodisering.utvid("B", 2.uke(2026)..3.uke(2026))

            utvidet.shouldBeInstanceOf<SammenhengendePeriodisering<String>>()
            utvidet.size shouldBe 2
            utvidet.first().periode shouldBe 2.uke(2026)
            utvidet.first().verdi shouldBe "A"
            utvidet.last().periode shouldBe 3.uke(2026)
            utvidet.last().verdi shouldBe "B"
        }

        @Test
        fun `utvider sammenhengendeperiodisering med en periode før og etter`() {
            val periodisering = SammenhengendePeriodisering(PeriodeMedVerdi("A", 2.uke(2026)))
            val utvidet = periodisering.utvid("B", 1.uke(2026)..3.uke(2026))

            utvidet.shouldBeInstanceOf<SammenhengendePeriodisering<String>>()
            utvidet.size shouldBe 3
            utvidet.first().periode shouldBe 1.uke(2026)
            utvidet.first().verdi shouldBe "B"
            utvidet[1].periode shouldBe 2.uke(2026)
            utvidet[1].verdi shouldBe "A"
            utvidet.last().periode shouldBe 3.uke(2026)
            utvidet.last().verdi shouldBe "B"
        }

        @Test
        fun `utvider ikke-sammenhengende periode med periode før`() {
            val periodisering = IkkesammenhengendePeriodisering(
                PeriodeMedVerdi("A", 2.uke(2026)),
                PeriodeMedVerdi("B", 4.uke(2026)),
            )
            val utvidet = periodisering.utvid("C", 1.uke(2026)..4.uke(2026))

            utvidet.shouldBeInstanceOf<IkkesammenhengendePeriodisering<String>>()
            utvidet.size shouldBe 3
            utvidet[0].periode shouldBe 1.uke(2026)
            utvidet[0].verdi shouldBe "C"
            utvidet[1].periode shouldBe 2.uke(2026)
            utvidet[1].verdi shouldBe "A"
            utvidet[2].periode shouldBe 4.uke(2026)
            utvidet[2].verdi shouldBe "B"
        }

        @Test
        fun `utvider ikke-sammenhengende periode med periode etter`() {
            val periodisering = IkkesammenhengendePeriodisering(
                PeriodeMedVerdi("A", 2.uke(2026)),
                PeriodeMedVerdi("B", 4.uke(2026)),
            )
            val utvidet = periodisering.utvid("C", 2.uke(2026)..5.uke(2026))

            utvidet.shouldBeInstanceOf<IkkesammenhengendePeriodisering<String>>()
            utvidet.size shouldBe 3
            utvidet[0].periode shouldBe 2.uke(2026)
            utvidet[0].verdi shouldBe "A"
            utvidet[1].periode shouldBe 4.uke(2026)
            utvidet[1].verdi shouldBe "B"
            utvidet[2].periode shouldBe 5.uke(2026)
            utvidet[2].verdi shouldBe "C"
        }

        @Test
        fun `utvider ikke-sammenhengende periode med periode før og etter`() {
            val periodisering = IkkesammenhengendePeriodisering(
                PeriodeMedVerdi("A", 2.uke(2026)),
                PeriodeMedVerdi("B", 4.uke(2026)),
            )
            val utvidet = periodisering.utvid("C", 1.uke(2026)..5.uke(2026))

            utvidet.shouldBeInstanceOf<IkkesammenhengendePeriodisering<String>>()
            utvidet.size shouldBe 4
            utvidet[0].periode shouldBe 1.uke(2026)
            utvidet[0].verdi shouldBe "C"
            utvidet[1].periode shouldBe 2.uke(2026)
            utvidet[1].verdi shouldBe "A"
            utvidet[2].periode shouldBe 4.uke(2026)
            utvidet[2].verdi shouldBe "B"
            utvidet[3].periode shouldBe 5.uke(2026)
            utvidet[3].verdi shouldBe "C"
        }
    }
}
