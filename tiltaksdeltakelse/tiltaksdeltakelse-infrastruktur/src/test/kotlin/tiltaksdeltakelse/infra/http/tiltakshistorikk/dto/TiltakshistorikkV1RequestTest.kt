package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.serialize
import org.junit.jupiter.api.Test

class TiltakshistorikkV1RequestTest {

    @Test
    fun `serialiserer identer som rene strenger`() {
        val request = TiltakshistorikkV1Request(
            identer = listOf(NorskIdentDto("12345678901"), NorskIdentDto("10987654321")),
        )

        serialize(request) shouldBe """{"identer":["12345678901","10987654321"]}"""
    }
}
