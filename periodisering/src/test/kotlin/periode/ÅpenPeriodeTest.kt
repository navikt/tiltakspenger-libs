package no.nav.tiltakspenger.libs.periode

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.desember
import no.nav.tiltakspenger.libs.dato.februar
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.dato.juni
import no.nav.tiltakspenger.libs.dato.mai
import no.nav.tiltakspenger.libs.dato.oktober
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ÅpenPeriodeTest {

    private val periode = Periode(1.januar(2025), 1.oktober(2025))

    @Test
    fun `periode - begge datoer satt - gir lukket periode`() {
        ÅpenPeriode(3.februar(2025), 1.juni(2025)).periode shouldBe Periode(3.februar(2025), 1.juni(2025))
    }

    @Test
    fun `periode - en eller begge datoer mangler - gir null`() {
        ÅpenPeriode(null, 1.juni(2025)).periode shouldBe null
        ÅpenPeriode(3.februar(2025), null).periode shouldBe null
        ÅpenPeriode(null, null).periode shouldBe null
    }

    @Test
    fun `init - fraOgMed etter tilOgMed - kaster`() {
        shouldThrow<IllegalArgumentException> {
            ÅpenPeriode(1.juni(2025), 3.februar(2025))
        }
    }

    @Test
    fun `init - dato på yttergrense som ikke kan danne en periode - kaster`() {
        shouldThrow<IllegalArgumentException> { ÅpenPeriode(LocalDate.MAX, LocalDate.MAX) }
        shouldThrow<IllegalArgumentException> { ÅpenPeriode(LocalDate.MIN, LocalDate.MIN) }
    }

    @Test
    fun `init - én dato på yttergrense - er lov`() {
        ÅpenPeriode(LocalDate.MAX, null).overlapperMed(periode) shouldBe Overlapp.Nei
        ÅpenPeriode(null, LocalDate.MIN).overlapperMed(periode) shouldBe Overlapp.Nei
    }

    @Test
    fun `overlapperMed periode - begge datoene mangler - returnerer kanskje`() {
        ÅpenPeriode(null, null).overlapperMed(periode) shouldBe Overlapp.Kanskje
    }

    @Test
    fun `overlapperMed periode - fom mangler, tom er før perioden - returnerer nei`() {
        ÅpenPeriode(null, 3.desember(2024)).overlapperMed(periode) shouldBe Overlapp.Nei
    }

    @Test
    fun `overlapperMed periode - fom mangler, tom er i perioden - returnerer ja`() {
        ÅpenPeriode(null, 3.mai(2025)).overlapperMed(periode) shouldBe Overlapp.Ja
    }

    @Test
    fun `overlapperMed periode - fom mangler, tom er etter perioden - returnerer kanskje`() {
        ÅpenPeriode(null, 3.mai(2026)).overlapperMed(periode) shouldBe Overlapp.Kanskje
    }

    @Test
    fun `overlapperMed periode - tom mangler, fom er før perioden - returnerer kanskje`() {
        ÅpenPeriode(3.desember(2024), null).overlapperMed(periode) shouldBe Overlapp.Kanskje
    }

    @Test
    fun `overlapperMed periode - tom mangler, fom er i perioden - returnerer ja`() {
        ÅpenPeriode(3.mai(2025), null).overlapperMed(periode) shouldBe Overlapp.Ja
    }

    @Test
    fun `overlapperMed periode - tom mangler, fom er etter perioden - returnerer nei`() {
        ÅpenPeriode(3.mai(2026), null).overlapperMed(periode) shouldBe Overlapp.Nei
    }

    @Test
    fun `overlapperMed periode - fom og tom er før perioden - returnerer nei`() {
        ÅpenPeriode(3.februar(2024), 1.juni(2024)).overlapperMed(periode) shouldBe Overlapp.Nei
    }

    @Test
    fun `overlapperMed periode - fom og tom er etter perioden - returnerer nei`() {
        ÅpenPeriode(3.februar(2026), 1.juni(2026)).overlapperMed(periode) shouldBe Overlapp.Nei
    }

    @Test
    fun `overlapperMed periode - fom og tom er innenfor perioden - returnerer ja`() {
        ÅpenPeriode(3.februar(2025), 1.juni(2025)).overlapperMed(periode) shouldBe Overlapp.Ja
    }

    @Test
    fun `overlapperMed periode - fom er før, tom er innenfor perioden - returnerer ja`() {
        ÅpenPeriode(3.februar(2024), 1.juni(2025)).overlapperMed(periode) shouldBe Overlapp.Ja
    }

    @Test
    fun `overlapperMed periode - tom er etter, fom er innenfor perioden - returnerer ja`() {
        ÅpenPeriode(3.februar(2025), 1.juni(2026)).overlapperMed(periode) shouldBe Overlapp.Ja
    }

    @Test
    fun `overlapperMed periode - fom er før, tom er etter perioden - returnerer ja`() {
        ÅpenPeriode(3.februar(2024), 1.juni(2026)).overlapperMed(periode) shouldBe Overlapp.Ja
    }

    @Test
    fun `overlapperMed åpen periode - begge er lukkede - svarer som lukkede perioder`() {
        ÅpenPeriode(3.februar(2025), 1.juni(2025)).overlapperMed(ÅpenPeriode(1.mai(2025), 1.juni(2026))) shouldBe Overlapp.Ja
        ÅpenPeriode(3.februar(2025), 1.juni(2025)).overlapperMed(ÅpenPeriode(1.januar(2026), 1.juni(2026))) shouldBe Overlapp.Nei
    }

    @Test
    fun `overlapperMed åpen periode - den ene er helt åpen - returnerer kanskje`() {
        ÅpenPeriode(null, null).overlapperMed(ÅpenPeriode(3.februar(2025), 1.juni(2025))) shouldBe Overlapp.Kanskje
        ÅpenPeriode(3.februar(2025), 1.juni(2025)).overlapperMed(ÅpenPeriode(null, null)) shouldBe Overlapp.Kanskje
        ÅpenPeriode(null, null).overlapperMed(ÅpenPeriode(null, null)) shouldBe Overlapp.Kanskje
    }

    @Test
    fun `overlapperMed åpen periode - lukket mot halvåpen - svarer som mot lukket periode`() {
        val lukket = ÅpenPeriode(periode.fraOgMed, periode.tilOgMed)
        lukket.overlapperMed(ÅpenPeriode(null, 3.desember(2024))) shouldBe Overlapp.Nei
        lukket.overlapperMed(ÅpenPeriode(null, 3.mai(2025))) shouldBe Overlapp.Ja
        lukket.overlapperMed(ÅpenPeriode(null, 3.mai(2026))) shouldBe Overlapp.Kanskje
        lukket.overlapperMed(ÅpenPeriode(3.desember(2024), null)) shouldBe Overlapp.Kanskje
        lukket.overlapperMed(ÅpenPeriode(3.mai(2025), null)) shouldBe Overlapp.Ja
        lukket.overlapperMed(ÅpenPeriode(3.mai(2026), null)) shouldBe Overlapp.Nei
    }

    @Test
    fun `overlapperMed åpen periode - begge er halvåpne og deler en dato - returnerer ja`() {
        ÅpenPeriode(3.februar(2025), null).overlapperMed(ÅpenPeriode(3.februar(2025), null)) shouldBe Overlapp.Ja
        ÅpenPeriode(null, 1.juni(2025)).overlapperMed(ÅpenPeriode(null, 1.juni(2025))) shouldBe Overlapp.Ja
        ÅpenPeriode(3.februar(2025), null).overlapperMed(ÅpenPeriode(null, 3.februar(2025))) shouldBe Overlapp.Ja
        ÅpenPeriode(null, 1.juni(2025)).overlapperMed(ÅpenPeriode(1.juni(2025), null)) shouldBe Overlapp.Ja
    }

    @Test
    fun `overlapperMed åpen periode - begge er halvåpne uten felles dato - returnerer kanskje`() {
        ÅpenPeriode(3.februar(2025), null).overlapperMed(ÅpenPeriode(4.februar(2025), null)) shouldBe Overlapp.Kanskje
        ÅpenPeriode(null, 1.juni(2025)).overlapperMed(ÅpenPeriode(null, 2.juni(2025))) shouldBe Overlapp.Kanskje
        ÅpenPeriode(3.februar(2025), null).overlapperMed(ÅpenPeriode(null, 1.juni(2025))) shouldBe Overlapp.Kanskje
        ÅpenPeriode(null, 1.juni(2025)).overlapperMed(ÅpenPeriode(3.februar(2025), null)) shouldBe Overlapp.Kanskje
    }

    @Test
    fun `overlapperMed åpen periode - den ene mangler begge datoer - returnerer kanskje`() {
        ÅpenPeriode(null, null).overlapperMed(ÅpenPeriode(3.februar(2025), null)) shouldBe Overlapp.Kanskje
        ÅpenPeriode(null, 1.juni(2025)).overlapperMed(ÅpenPeriode(null, null)) shouldBe Overlapp.Kanskje
    }

    @Test
    fun `overlapperMed åpen periode - likhet uavhengig av retning`() {
        val a = ÅpenPeriode(null, 3.desember(2024))
        val b = ÅpenPeriode(periode.fraOgMed, periode.tilOgMed)
        a.overlapperMed(b) shouldBe b.overlapperMed(a)

        val c = ÅpenPeriode(3.mai(2025), null)
        c.overlapperMed(b) shouldBe b.overlapperMed(c)
    }
}
