package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenJackson2Test {
    private val scope = fixtureScope("jackson2")

    @Test
    fun `flagger Jackson 2-importer, men ikke annotation-artefakten eller Jackson 3`() {
        val brudd = IngenJackson2.brudd(scope)

        brudd shouldHaveSize 2
        brudd[0] shouldContain "com.fasterxml.jackson.databind.ObjectMapper"
        brudd[1] shouldContain "com.fasterxml.jackson.module.kotlin.KotlinModule"
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenJackson2.assert(scope) }
        feil.message shouldContain "Bruk Jackson 3"
        feil.message shouldContain "Fant 2 brudd"
    }
}
