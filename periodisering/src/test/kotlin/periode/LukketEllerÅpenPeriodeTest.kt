package no.nav.tiltakspenger.libs.periode

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.dato.juni
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LukketEllerÅpenPeriodeTest {

    private fun beskriv(periode: LukketEllerÅpenPeriode): String = when (periode) {
        is Periode -> "lukket ${periode.fraOgMed}–${periode.tilOgMed}"
        is ÅpenPeriode -> "åpen ${periode.fraOgMed}–${periode.tilOgMed}"
    }

    @Test
    fun `datoene kan leses gjennom interfacet for begge typene`() {
        val perioder: List<LukketEllerÅpenPeriode> = listOf(
            Periode(1.januar(2025), 1.juni(2025)),
            ÅpenPeriode(1.januar(2025), null),
        )

        perioder.map { it.fraOgMed } shouldBe listOf(1.januar(2025), 1.januar(2025))
        perioder.map { it.tilOgMed } shouldBe listOf<LocalDate?>(1.juni(2025), null)
    }

    @Test
    fun `when over interfacet er uttømmende uten else`() {
        beskriv(Periode(1.januar(2025), 1.juni(2025))) shouldBe "lukket 2025-01-01–2025-06-01"
        beskriv(ÅpenPeriode(null, 1.juni(2025))) shouldBe "åpen null–2025-06-01"
    }
}
