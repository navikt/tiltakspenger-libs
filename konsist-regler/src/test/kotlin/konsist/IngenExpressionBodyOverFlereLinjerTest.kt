package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenExpressionBodyOverFlereLinjerTest {
    private val scope = fixtureScope("expressionbody")

    @Test
    fun `flagger funksjoner med expression body over flere linjer, men ikke én linje, signatur over flere linjer eller blokk`() {
        val brudd = IngenExpressionBodyOverFlereLinjer.brudd(scope)

        brudd shouldHaveSize 2
        brudd.joinToString("\n") shouldContain "overFlereLinjer"
        brudd.joinToString("\n") shouldContain "nestet"
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        IngenExpressionBodyOverFlereLinjer.brudd(scope, unntatteFilstier = setOf("expressionbody/Brudd.kt")).shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenExpressionBodyOverFlereLinjer.assert(scope) }
        feil.message shouldContain "Fant 2 brudd"
    }
}
