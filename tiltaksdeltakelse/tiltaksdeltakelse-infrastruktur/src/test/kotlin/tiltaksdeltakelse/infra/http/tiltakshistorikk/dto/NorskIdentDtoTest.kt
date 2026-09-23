package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.FnrGenerator
import org.junit.jupiter.api.Test

class NorskIdentDtoTest {

    @Test
    fun `maskerer verdien i toString`() {
        NorskIdentDto(FnrGenerator().generer().verdi).toString() shouldBe "***********"
    }

    @Test
    fun `verdien er tilgjengelig gjennom feltet`() {
        val fnr = FnrGenerator().generer().verdi
        NorskIdentDto(fnr).verdi shouldBe fnr
    }
}
