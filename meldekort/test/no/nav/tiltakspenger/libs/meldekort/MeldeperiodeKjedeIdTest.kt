package no.nav.tiltakspenger.libs.common

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.meldekort.MeldeperiodeKjedeId
import no.nav.tiltakspenger.libs.periodisering.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class MeldeperiodeKjedeIdTest {

    @Test
    fun `får riktig verdi og toString`() {
        val periode = Periode(fraOgMed = LocalDate.of(2021, 3, 1), tilOgMed = LocalDate.of(2021, 3, 14))
        val meldeperiodeKjedeId = MeldeperiodeKjedeId.fraPeriode(periode)
        "2021-03-01" shouldBe meldeperiodeKjedeId.fraOgMed.toString()
        "2021-03-14" shouldBe meldeperiodeKjedeId.tilOgMed.toString()
        meldeperiodeKjedeId.verdi shouldBe "2021-03-01/2021-03-14"
    }
}
