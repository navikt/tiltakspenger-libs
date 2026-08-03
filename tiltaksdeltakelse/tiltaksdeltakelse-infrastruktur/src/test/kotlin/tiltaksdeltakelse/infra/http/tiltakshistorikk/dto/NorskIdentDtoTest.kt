package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NorskIdentDtoTest {

    @Test
    fun `maskerer verdien i toString`() {
        NorskIdentDto("12345678901").toString() shouldBe "***********"
    }

    @Test
    fun `verdien er tilgjengelig gjennom feltet`() {
        NorskIdentDto("12345678901").verdi shouldBe "12345678901"
    }
}
