package no.nav.tiltakspenger.libs.periode

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.periodisering.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class PeriodeAntallDagerTest {
    @Test
    fun `en dag`() {
        val periode = Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 1))
        periode.antallDager shouldBe 1
    }

    @Test
    fun `tar med første og siste dag`() {
        val periode = Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 10))
        periode.antallDager shouldBe 10
    }
}
