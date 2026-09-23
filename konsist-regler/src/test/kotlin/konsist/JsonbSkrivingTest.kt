package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class JsonbSkrivingTest {
    private val scope = fixtureScope("jsonbskriving")

    @Test
    fun `flagger innpakket parameter og json-cast, men ikke bar cast eller kolonnefunksjon`() {
        val brudd = JsonbSkriving.bruddBarCast(scope)

        // to_jsonb(:payload), to_json(:payload::jsonb), :payload::json og :payload::JSON.
        brudd shouldHaveSize 4
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "to_jsonb(:payload"
        samlet shouldContain "to_json(:payload"
        samlet shouldContain ":payload::json"
        samlet shouldContain ":payload::JSON"
        samlet shouldNotContain "Ren.kt"
    }

    @Test
    fun `flagger PGobject i både import og bruk, og oppgir linjenummer`() {
        val brudd = JsonbSkriving.bruddPGobject(scope)

        brudd shouldHaveSize 2
        val samlet = brudd.joinToString("\n")
        samlet shouldContain "Brudd.kt:3: bruker PGobject"
        samlet shouldContain "Brudd.kt:9: bruker PGobject"
        samlet shouldNotContain "Ren.kt"
    }

    /** `Ren.kt` dokumenterer mønstrene den advarer mot, i både KDoc og linjekommentar. */
    @Test
    fun `kommentarer som viser mønsteret er ikke brudd`() {
        JsonbSkriving.bruddBarCast(scope).filter { brudd -> "Ren.kt" in brudd }.shouldBeEmpty()
        JsonbSkriving.bruddPGobject(scope).filter { brudd -> "Ren.kt" in brudd }.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        JsonbSkriving.bruddBarCast(scope, unntatteFilstier = setOf("jsonbskriving/Brudd.kt")).shouldBeEmpty()
        JsonbSkriving.bruddPGobject(scope, unntatteFilstier = setOf("jsonbskriving/Brudd.kt")).shouldBeEmpty()
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        shouldThrow<AssertionError> {
            JsonbSkriving.assertBarCast(scope)
        }.message shouldContain "Skriv jsonb-parametre som `:navn::jsonb`"

        shouldThrow<AssertionError> {
            JsonbSkriving.assertIngenPGobject(scope)
        }.message shouldContain "navngitt `*DbJson`-type"
    }

    /** Uten vakten består begge reglene trivielt i et arbeidstre, der Konsist-scopet blir tomt. */
    @Test
    fun `vakten slår ut når skanningen ikke finner jsonb-parametre`() {
        JsonbSkriving.assertFinnerJsonbParametre(scope, minstAntallFiler = 2)

        shouldThrow<AssertionError> {
            JsonbSkriving.assertFinnerJsonbParametre(scope, minstAntallFiler = 3)
        }.message shouldContain "fant 2 filer med jsonb-parametre"
    }
}
