package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class IngenMuterbareTestfelterTest {
    private val scope = fixtureScope("muterbaretestfelter")

    @Test
    fun `flagger var, lateinit var og muterbare initialisatorer i testklasser og på toppnivå`() {
        val brudd = IngenMuterbareTestfelter.brudd(scope)

        // Toppnivå-var, to var-felter, køen og mocken i testklassen.
        brudd shouldHaveSize 5
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "toppnivåTeller"
        samlet shouldContain "teller"
        samlet shouldContain "klient"
        samlet shouldContain "kø"
        samlet shouldContain "fakeRepo"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `immutable felter, lokale variabler og klasser uten tester flagges ikke`() {
        IngenMuterbareTestfelter.brudd(scope).filter { brudd -> "Ren.kt" in brudd }.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        IngenMuterbareTestfelter.brudd(scope, unntatteFilstier = setOf("muterbaretestfelter/Brudd.kt")).shouldBeEmpty()
    }

    /** En muterbar testtype repoet definerer selv er ikke i standardsettet, og fanges først når navnet legges til. */
    @Test
    fun `ekstra muterbare initialisatorer utvider standardsettet`() {
        val brudd = IngenMuterbareTestfelter.brudd(scope, ekstraMuterbareInitialisatorer = setOf("Hendelseskø"))

        brudd shouldHaveSize 6
        brudd.joinToString("\n") shouldContain "hendelser"
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenMuterbareTestfelter.assert(scope) }
        feil.message shouldContain "Ingen muterbar tilstand i testklassers felter"
    }
}
