package no.nav.tiltakspenger.libs.common

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.personopplysning.Fnr
import org.junit.jupiter.api.Test

internal class FnrExTest {
    @Test
    fun `random gir elleve sifre`() {
        val verdi = Fnr.random().verdi

        verdi.length shouldBe 11
        verdi.all { it.isDigit() } shouldBe true
    }
}
