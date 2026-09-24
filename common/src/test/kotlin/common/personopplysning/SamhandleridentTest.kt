package no.nav.tiltakspenger.libs.common.personopplysning

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class SamhandleridentTest {
    @Test
    fun `samhandlerident maskerer seg selv og i data-klasser`() {
        val samhandlerident = Samhandlerident("alle nitall")

        samhandlerident.toString() shouldBe "*****"
        samhandlerident.toString() shouldNotContain "alle nitall"

        data class Utbetaling(val samhandlerident: Samhandlerident)

        Utbetaling(samhandlerident).toString() shouldBe "Utbetaling(samhandlerident=*****)"
        Utbetaling(samhandlerident).toString() shouldNotContain "alle nitall"
    }

    @Test
    fun `begrunnelse er ikke tom`() {
        Samhandlerident("000000000").begrunnelse.isNotBlank() shouldBe true
    }

    @Test
    fun `blank samhandlerident avvises`() {
        shouldThrowWithMessage<IllegalArgumentException>("Samhandlerident kan ikke være tom") {
            Samhandlerident("")
        }
        shouldThrowWithMessage<IllegalArgumentException>("Samhandlerident kan ikke være tom") {
            Samhandlerident("   ")
        }
    }

    @Test
    fun `fabrikken gjør blank til fravær`() {
        samhandlerident(null) shouldBe null
        samhandlerident("") shouldBe null
        samhandlerident("   ") shouldBe null
        samhandlerident("alle nitall") shouldBe Samhandlerident("alle nitall")
    }
}
