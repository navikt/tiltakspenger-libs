package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class IngenGlobalMockingTest {
    private val scope = fixtureScope("globalmocking")

    @Test
    fun `flagger importer og kall av globale mockk-funksjoner`() {
        val brudd = IngenGlobalMocking.brudd(scope)

        // To importer, de to kallene deres, og det fullkvalifiserte kallet uten import.
        brudd shouldHaveSize 5
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "io.mockk.mockkStatic"
        samlet shouldContain "io.mockk.clearAllMocks"
        samlet shouldContain "mockkObject"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `mockk og spyk per test, kommentarer og strengliteraler flagges ikke`() {
        IngenGlobalMocking.brudd(scope).filter { brudd -> "Ren.kt" in brudd }.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        IngenGlobalMocking.brudd(scope, unntatteFilstier = setOf("globalmocking/Brudd.kt")).shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenGlobalMocking.assert(scope) }
        feil.message shouldContain "Ingen global mocking i tester"
    }
}
