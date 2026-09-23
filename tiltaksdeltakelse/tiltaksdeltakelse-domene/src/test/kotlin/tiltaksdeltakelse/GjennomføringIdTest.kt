package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class GjennomføringIdTest {
    @Test
    fun `bærer verdien fra kilden uendret`() {
        GjennomføringId("6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b").verdi shouldBe "6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b"
    }

    @Test
    fun `toString gir den rå verdien`() {
        GjennomføringId("6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b").toString() shouldBe "6f3b1f52-9a1e-4a34-8f9a-1c2d3e4f5a6b"
    }

    /**
     * Den gamle modellen brukte tom streng for «ingen gjennomføring» (Arena og Team Tiltak).
     * Her er tom streng ugyldig, og fraværet uttrykkes med `null` på deltakelsen.
     */
    @Test
    fun `tom id avvises - fravær uttrykkes med null`() {
        shouldThrowWithMessage<IllegalArgumentException>("GjennomføringId kan ikke være tom") {
            GjennomføringId("")
        }
        shouldThrowWithMessage<IllegalArgumentException>("GjennomføringId kan ikke være tom") {
            GjennomføringId("   ")
        }
    }
}
