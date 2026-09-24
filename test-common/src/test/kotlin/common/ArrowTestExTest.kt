package no.nav.tiltakspenger.libs.common

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class ArrowTestExTest {
    @Test
    fun `getOrFail gir høyresiden`() {
        "verdi".right().getOrFail() shouldBe "verdi"
        "verdi".right().getOrFail("kontekst") shouldBe "verdi"
    }

    @Test
    fun `getOrFail feiler med venstresiden i meldingen`() {
        shouldThrowWithMessage<AssertionError>("noe gikk galt") {
            "noe gikk galt".left().getOrFail()
        }
        shouldThrowWithMessage<AssertionError>("Message: ved lagring, Error: noe gikk galt") {
            "noe gikk galt".left().getOrFail("ved lagring")
        }
    }
}
