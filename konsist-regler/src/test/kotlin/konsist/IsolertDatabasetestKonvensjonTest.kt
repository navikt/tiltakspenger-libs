package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class IsolertDatabasetestKonvensjonTest {
    private val scope = fixtureScope("isolertdatabasetest")

    @Test
    fun `flagger runIsolated uten annotasjon og utenfor test`() {
        val brudd = IsolertDatabasetestKonvensjon.runIsolatedUtenAnnotasjon(scope)

        brudd shouldHaveSize 2
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "utenfor en test"
        samlet shouldContain "uten @IsolatedDatabaseTest"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `annotasjonsnavnet er konfigurerbart`() {
        val brudd = IsolertDatabasetestKonvensjon.runIsolatedUtenAnnotasjon(scope, annotasjonsnavn = "EtAnnetNavn")

        // Med et annet annotasjonsnavn mangler alle bruksstedene i testene annotasjonen, også de i Ren.kt.
        brudd shouldHaveSize 5
    }

    @Test
    fun `flagger runIsolated uten begrunnelse`() {
        val brudd = IsolertDatabasetestKonvensjon.runIsolatedUtenBegrunnelse(scope)

        brudd shouldHaveSize 3
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "uten begrunnelse"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `begrunnelse og exit-plan-TODO godtas, kommentarer og strenger flagges ikke`() {
        val samlet = (
            IsolertDatabasetestKonvensjon.runIsolatedUtenAnnotasjon(scope) +
                IsolertDatabasetestKonvensjon.runIsolatedUtenBegrunnelse(scope)
            ).joinToString("\n")

        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        IsolertDatabasetestKonvensjon
            .runIsolatedUtenAnnotasjon(scope, unntatteFilstier = setOf("isolertdatabasetest/Brudd.kt"))
            .shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val annotasjonsfeil = shouldThrow<AssertionError> { IsolertDatabasetestKonvensjon.assertRunIsolatedHarAnnotasjon(scope) }
        annotasjonsfeil.message shouldContain "serialiserer dem under parallellkjøring"

        val begrunnelsesfeil = shouldThrow<AssertionError> { IsolertDatabasetestKonvensjon.assertRunIsolatedHarBegrunnelse(scope) }
        begrunnelsesfeil.message shouldContain "exit-plan-TODO"
    }
}
