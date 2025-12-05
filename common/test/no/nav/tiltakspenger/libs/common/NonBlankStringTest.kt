package no.nav.tiltakspenger.libs.common

import no.nav.tiltakspenger.libs.common.NonBlankString.Companion.toNonBlankString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class NonBlankStringTest {

    @Test
    fun `exception dersom strengen er blank`() {
        assertThrows<IllegalArgumentException> {
            NonBlankString.create("")
            "".toNonBlankString()
        }
    }

    @Test
    fun `exception dersom strengen er bare en space`() {
        assertThrows<IllegalArgumentException> {
            NonBlankString.create(" ")
            " ".toNonBlankString()
        }
    }

    @Test
    fun `exception dersom strengen er newline`() {
        assertThrows<IllegalArgumentException> {
            NonBlankString.create("\n")
            "\n".toNonBlankString()
        }
    }

    @Test
    fun `konstruerer NonBlankString`() {
        assertDoesNotThrow {
            NonBlankString.create("a")
            "a".toNonBlankString()
        }
    }
}
