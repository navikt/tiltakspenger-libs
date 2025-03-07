package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.BehandlingId
import no.nav.tiltakspenger.libs.common.SakId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep

internal class UlidTest {

    @Test
    fun `test roundtrip`() {
        val ulid = BehandlingId.random()
        val ulid2 = BehandlingId.fromString(ulid.toString())
        assertEquals(ulid, ulid2)
    }

    @Test
    fun `test prefixPart and ulidPart`() {
        val ulid = BehandlingId.random()
        val ulid2 = BehandlingId.fromString("${ulid.prefixPart()}_${ulid.ulidPart()}")
        assertEquals(ulid, ulid2)
    }

    @Test
    fun `test fromString negativ test`() {
        val utenGyldigSkilletegn = "beh"
        shouldThrowWithMessage<IllegalArgumentException>("Ikke gyldig Id ($utenGyldigSkilletegn), skal bestå av to deler skilt med _") {
            BehandlingId.fromString(utenGyldigSkilletegn)
        }

        val utenUlid = "beh_"
        shouldThrowWithMessage<IllegalArgumentException>("Ikke gyldig Id ($utenUlid), ulid er ugyldig") {
            BehandlingId.fromString(utenUlid)
        }

        val ugyldigTegniUlid = "beh_1234567890123456789U123456"
        shouldThrowWithMessage<IllegalArgumentException>("Ikke gyldig Id ($ugyldigTegniUlid), ulid er ugyldig") {
            BehandlingId.fromString(ugyldigTegniUlid)
        }

        val ugyldigUlid = "beh_UU_JJ"
        shouldThrowWithMessage<IllegalArgumentException>("Ikke gyldig Id ($ugyldigUlid), skal bestå av prefiks + ulid") {
            BehandlingId.fromString(ugyldigUlid)
        }

        val feilPrefix = "baloo_"
        shouldThrowWithMessage<IllegalArgumentException>("Prefix må starte med beh. Dette er nok ikke en BehandlingId ($feilPrefix)") {
            BehandlingId.fromString(feilPrefix)
        }
    }

    @Test
    fun `test konvertering av BehandlingId til UUID og tilbake igjen til ULID`() {
        repeat(100) {
            val opprinneligBehandlingId = BehandlingId.random()
            val uuid = opprinneligBehandlingId.uuid()
            val behandlingIdFraUUID = BehandlingId.fromUUID(uuid)

            opprinneligBehandlingId shouldBe behandlingIdFraUUID
        }
    }

    @Test
    fun `test compareTo av ULID`() {
        repeat(100) {
            val førsteId = SakId.random()
            sleep(1)
            val andreId = SakId.random()

            andreId shouldBeGreaterThan førsteId
        }
    }
}
