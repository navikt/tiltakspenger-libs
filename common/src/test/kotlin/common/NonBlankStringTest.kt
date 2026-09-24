package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import no.nav.tiltakspenger.libs.common.NonBlankString.Companion.toNonBlankString
import org.junit.jupiter.api.Test

class NonBlankStringTest {

    @Test
    fun `exception dersom strengen er blank`() {
        shouldThrow<IllegalArgumentException> {
            NonBlankString.create("")
            "".toNonBlankString()
        }
    }

    @Test
    fun `exception dersom strengen er bare en space`() {
        shouldThrow<IllegalArgumentException> {
            NonBlankString.create(" ")
            " ".toNonBlankString()
        }
    }

    @Test
    fun `exception dersom strengen er newline`() {
        shouldThrow<IllegalArgumentException> {
            NonBlankString.create("\n")
            "\n".toNonBlankString()
        }
    }

    @Test
    fun `konstruerer NonBlankString`() {
        shouldNotThrowAny {
            NonBlankString.create("a")
            "a".toNonBlankString()
        }
    }
}
