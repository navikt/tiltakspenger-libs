package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.serialize
import org.junit.jupiter.api.Test

class TiltakshistorikkV1RequestTest {

    /**
     * Strengen pinnes eksakt: kontraktens kotlinx-modell har ingen default på `maxAgeYears`, så nøkkelen må stå i JSON-en selv når verdien er null.
     * Testen pinner også at identene serialiseres som rene strenger (value class pakkes ut), ikke som objekter.
     */
    @Test
    fun `serialiserer identer som rene strenger og beholder maxAgeYears-nøkkelen når den er null`() {
        val request = TiltakshistorikkV1Request(
            identer = listOf(NorskIdentDto("12345678901"), NorskIdentDto("10987654321")),
            maxAgeYears = null,
        )

        serialize(request) shouldBe """{"identer":["12345678901","10987654321"],"maxAgeYears":null}"""
    }
}
