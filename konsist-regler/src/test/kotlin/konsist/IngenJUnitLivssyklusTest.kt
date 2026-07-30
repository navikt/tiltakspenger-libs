package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class IngenJUnitLivssyklusTest {
    private val scope = fixtureScope("junitlivssyklus")

    @Test
    fun `flagger importer og fullkvalifisert bruk av livssyklusannotasjoner`() {
        val brudd = IngenJUnitLivssyklus.brudd(scope)

        // To importer pluss den fullkvalifiserte annotasjonen uten import.
        brudd shouldHaveSize 3
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "org.junit.jupiter.api.BeforeEach"
        samlet shouldContain "org.junit.jupiter.api.TestInstance"
        samlet shouldContain "org.junit.jupiter.api.AfterEach"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `vanlige tester, kommentarer og strengliteraler flagges ikke`() {
        IngenJUnitLivssyklus.brudd(scope).filter { brudd -> "Ren.kt" in brudd }.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        IngenJUnitLivssyklus.brudd(scope, unntatteFilstier = setOf("junitlivssyklus/Brudd.kt")).shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenJUnitLivssyklus.assert(scope) }
        feil.message shouldContain "Ingen JUnit-livssyklus i tester"
    }
}
