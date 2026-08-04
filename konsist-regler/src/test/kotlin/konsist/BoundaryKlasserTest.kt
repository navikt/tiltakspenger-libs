package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class BoundaryKlasserTest {
    private val scope = fixtureScope("boundary")

    @Test
    fun `flagger boundary-navn utenfor infra-pakker, men ikke i infra-pakker eller vanlige navn`() {
        val brudd = BoundaryKlasser.brudd(scope)

        brudd shouldHaveSize 2
        brudd[0] shouldContain "data class NoeDTO"
        brudd[1] shouldContain "class SvarResponse"
    }

    @Test
    fun `tillatteFiler unntar bevisste unntak`() {
        val brudd = BoundaryKlasser.brudd(scope, tillatteFiler = setOf("boundary/Brudd.kt"))

        brudd shouldHaveSize 0
    }

    /** Med `domene` som infra-segment i tillegg regnes også bruddfila som infrastruktur, og da er det ingenting igjen å flagge. */
    @Test
    fun `ekstra infra-segmenter utvider hva som regnes som infrastruktur`() {
        BoundaryKlasser.brudd(scope, ekstraInfraSegmenter = setOf("domene")) shouldHaveSize 0
    }

    @Test
    fun `ekstra boundary-suffikser utvider hva som regnes som en boundary-type`() {
        val brudd = BoundaryKlasser.brudd(scope, ekstraBoundarySuffikser = setOf("Kommando"))

        brudd shouldHaveSize 3
        brudd.joinToString("\n") shouldContain "class OpprettKommando"
    }
}
