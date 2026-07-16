package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenJUnit4Test {
    @Test
    fun `flagger JUnit 4-importer, men ikke jupiter eller platform`() {
        val brudd = IngenJUnit4.brudd(fixtureScope("junit4"))

        brudd shouldHaveSize 3
        brudd[0] shouldContain "junit.framework.TestCase"
        brudd[1] shouldContain "org.junit.Assert"
        brudd[2] shouldContain "org.junit.Test"
    }
}
