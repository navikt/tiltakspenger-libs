package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep

internal class UlidTest {

    @Test
    fun `test roundtrip`() {
        val ulid = RammebehandlingId.random()
        val ulid2 = RammebehandlingId.fromString(ulid.toString())
        ulid2 shouldBe ulid
    }

    @Test
    fun `test prefixPart and ulidPart`() {
        val ulid = RammebehandlingId.random()
        val ulid2 = RammebehandlingId.fromString("${ulid.prefixPart()}_${ulid.ulidPart()}")
        ulid2 shouldBe ulid
    }

    @Test
    fun `test fromString negativ test`() {
        val utenGyldigSkilletegn = "beh"
        shouldThrowWithMessage<IllegalArgumentException>("Ikke gyldig Id ($utenGyldigSkilletegn), skal bestå av prefiks + ulid") {
            RammebehandlingId.fromString(utenGyldigSkilletegn)
        }

        val utenUlid = "beh_"
        shouldThrowWithMessage<IllegalArgumentException>("Ikke gyldig Id ($utenUlid), ulid er ugyldig") {
            RammebehandlingId.fromString(utenUlid)
        }

        val ugyldigTegniUlid = "beh_1234567890123456789U123456"
        shouldThrowWithMessage<IllegalArgumentException>("Ikke gyldig Id ($ugyldigTegniUlid), ulid er ugyldig") {
            RammebehandlingId.fromString(ugyldigTegniUlid)
        }

        val ugyldigUlid = "beh_UU_JJ"
        shouldThrowWithMessage<IllegalArgumentException>("Ikke gyldig Id ($ugyldigUlid), skal bestå av prefiks + ulid") {
            RammebehandlingId.fromString(ugyldigUlid)
        }

        val feilPrefix = "baloo_"
        shouldThrowWithMessage<IllegalArgumentException>("Prefix må starte med beh. Dette er nok ikke en BehandlingId ($feilPrefix)") {
            RammebehandlingId.fromString(feilPrefix)
        }
    }

    @Test
    fun `test konvertering av BehandlingId til UUID og tilbake igjen til ULID`() {
        repeat(100) {
            val opprinneligBehandlingId = RammebehandlingId.random()
            val uuid = opprinneligBehandlingId.uuid()
            val behandlingIdFraUUID = RammebehandlingId.fromUUID(uuid)

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
