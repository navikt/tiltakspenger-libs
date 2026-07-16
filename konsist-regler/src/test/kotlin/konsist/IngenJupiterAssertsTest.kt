package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenJupiterAssertsTest {
    @Test
    fun `flagger Jupiter assertions, men ikke kotest eller jupiter Test`() {
        val brudd = IngenJupiterAsserts.brudd(fixtureScope("jupiterasserts"))

        brudd shouldHaveSize 3
        brudd[0] shouldContain "org.junit.jupiter.api.Assertions"
        brudd[1] shouldContain "org.junit.jupiter.api.Assertions.assertEquals"
        brudd[2] shouldContain "org.junit.jupiter.api.assertThrows"
    }
}
