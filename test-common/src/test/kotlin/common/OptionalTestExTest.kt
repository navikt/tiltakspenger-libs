package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Optional

internal class OptionalTestExTest {
    @Test
    fun `getOrFail gir verdien når den finnes`() {
        Optional.of("verdi").getOrFail() shouldBe "verdi"
        Optional.of("verdi").getOrFail("kontekst") shouldBe "verdi"
    }

    @Test
    fun `getOrFail feiler med lesbar melding når Optional-en er tom`() {
        shouldThrowWithMessage<AssertionError>("Forventet en verdi, men Optional-en var tom.") {
            Optional.empty<String>().getOrFail()
        }
        shouldThrowWithMessage<AssertionError>("Message: header manglet, Error: Optional-en var tom.") {
            Optional.empty<String>().getOrFail("header manglet")
        }
    }
}
