package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenBackticksUtenMellomromTest {
    private val scope = fixtureScope("backticks")

    @Test
    fun `flagger backticks rundt navn uten mellomrom, men ikke testnavn, nøkkelord, kommentarer eller strenger`() {
        val brudd = IngenBackticksUtenMellomrom.brudd(scope)

        brudd shouldHaveSize 4
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "`funksjonUtenMellomrom`"
        samlet shouldContain "`KlasseUtenMellomrom`"
        samlet shouldContain "`variabelUtenMellomrom`"
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenBackticksUtenMellomrom.assert(scope) }
        feil.message shouldContain "Backticks rundt navn er kun for testnavn med mellomrom"
        feil.message shouldContain "Fant 4 brudd"
    }
}
