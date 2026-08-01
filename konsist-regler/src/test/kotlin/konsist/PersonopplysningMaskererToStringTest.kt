package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class PersonopplysningMaskererToStringTest {
    private val scope = fixtureScope("personopplysning")

    @Test
    fun `flagger data class som lener seg på generert toString`() {
        val brudd = PersonopplysningMaskererToString.brudd(scope)

        brudd shouldHaveSize 2
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "LekkerFødselsnummer"
        samlet shouldContain "LekkerArrangørnavn"
    }

    /**
     * Value class-en fanges allerede av kompilatoren, og data class-en med egen toString er mønsteret Fnr bruker.
     * En klasse uten markering er ikke regelens bord.
     */
    @Test
    fun `flagger ikke typer som maskerer selv eller ikke markerer seg`() {
        PersonopplysningMaskererToString.brudd(scope).joinToString("\n") shouldNotContain "Ren.kt"
    }

    @Test
    fun `markørene kan overstyres`() {
        PersonopplysningMaskererToString.brudd(scope, markører = setOf("NoeHeltAnnet")).shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { PersonopplysningMaskererToString.assert(scope) }
        feil.message shouldContain "må deklarere sin egen toString()"
    }
}
