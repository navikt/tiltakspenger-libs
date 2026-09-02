package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.json.serialize
import org.junit.jupiter.api.Test

class TiltakshistorikkV1RequestTest {

    @Test
    fun `serialiserer identer som rene strenger`() {
        val generator = FnrGenerator()
        val fnr = generator.generer().verdi
        val historiskFnr = generator.generer().verdi
        val request = TiltakshistorikkV1Request(
            identer = listOf(NorskIdentDto(fnr), NorskIdentDto(historiskFnr)),
        )

        serialize(request) shouldBe """{"identer":["$fnr","$historiskFnr"]}"""
    }
}
