package no.nav.tiltakspenger.libs.common.felles

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.MeldeperiodeId
import no.nav.tiltakspenger.libs.periodisering.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class MeldeperiodeIdTest {

    @Test
    fun `test meldeperiodeid`() {
        val periode = Periode(fraOgMed = LocalDate.of(2021, 3, 1), tilOgMed = LocalDate.of(2021, 3, 14))
        val meldeperiodeId = MeldeperiodeId.fraPeriode(periode)
        "2021-03-01" shouldBe meldeperiodeId.fraOgMed.toString()
        "2021-03-14" shouldBe meldeperiodeId.tilOgMed.toString()
        meldeperiodeId.verdi shouldBe "2021-03-01/2021-03-14"
    }
}
