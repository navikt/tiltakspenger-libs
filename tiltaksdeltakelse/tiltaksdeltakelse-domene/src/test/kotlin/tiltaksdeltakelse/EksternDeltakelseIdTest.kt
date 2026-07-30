package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class EksternDeltakelseIdTest {
    @Test
    fun `bærer verdien fra kilden uendret`() {
        EksternDeltakelseId("TA1234567").verdi shouldBe "TA1234567"
        EksternDeltakelseId("6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b").verdi shouldBe "6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b"
    }

    @Test
    fun `toString gir den rå verdien`() {
        EksternDeltakelseId("TA1234567").toString() shouldBe "TA1234567"
    }

    /**
     * IDen er nøkkelen deltakelser slås opp på, både i samletypen vår og når vi matcher mot søknadens `aktivitetId`.
     * I den bruken bokses verdiklassen, så likhet og hashkode må oppføre seg som for strengen den bærer.
     */
    @Test
    fun `virker som nøkkel i en samling`() {
        val id = EksternDeltakelseId("TA1234567")

        mapOf(id to "en deltakelse")[EksternDeltakelseId("TA1234567")] shouldBe "en deltakelse"
        setOf(id, EksternDeltakelseId("TA1234567")).size shouldBe 1
        listOf(id).single().verdi shouldBe "TA1234567"
    }

    @Test
    fun `kjenner igjen Arena-formen`() {
        EksternDeltakelseId("TA1234567").harTaPrefiks shouldBe true
        EksternDeltakelseId("6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b").harTaPrefiks shouldBe false
    }

    @Test
    fun `kjenner igjen UUID-formen`() {
        EksternDeltakelseId("6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b").erUuid shouldBe true
        EksternDeltakelseId("6F3B1F52-9A1E-4A34-8F9A-1C2D3E4F5A6B").erUuid shouldBe true
        EksternDeltakelseId("TA1234567").erUuid shouldBe false
    }

    /**
     * `UUID.fromString` godtar for korte grupper og ville sagt ja til denne.
     * Regexen er strengere, og kaster ikke.
     */
    @Test
    fun `avkortet UUID er ikke en UUID`() {
        EksternDeltakelseId("6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6").erUuid shouldBe false
        EksternDeltakelseId("1-1-1-1-1").erUuid shouldBe false
    }

    @Test
    fun `tom id avvises`() {
        shouldThrowWithMessage<IllegalArgumentException>("EksternDeltakelseId kan ikke være tom") {
            EksternDeltakelseId("")
        }
        shouldThrowWithMessage<IllegalArgumentException>("EksternDeltakelseId kan ikke være tom") {
            EksternDeltakelseId("   ")
        }
    }
}
