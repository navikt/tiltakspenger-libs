package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenInternalModifierTest {
    private val scope = fixtureScope("internalmodifier")

    @Test
    fun `flagger alle erklæringstypene som kan bære internal`() {
        val brudd = IngenInternalModifier.brudd(scope).map { linje -> linje.substringAfterLast(": ") }

        brudd shouldContainExactlyInAnyOrder listOf(
            "Saksnummer",
            "TAK",
            "terskel",
            "beregn",
            "beregnDokumentert",
            "Beregner",
            "sats",
            "kjør",
            "Hjelper",
            "Regel",
            "Registeret",
            "Status",
            "Rad",
            "konstruktøren i MedInternalKonstruktør",
            "setteren til tilstand",
        )
    }

    @Test
    fun `rapporterer fil og linjenummer for hvert brudd`() {
        val brudd = IngenInternalModifier.brudd(scope)

        brudd.forEach { linje -> linje shouldContain "internalmodifier/Brudd.kt:" }
    }

    /**
     * Konsist plasserer en dokumentert erklæring på KDoc-ens første linje, her linje 11.
     * Meldingen skal peke på linje 15, der modifikatoren faktisk står — ellers sender den leseren til dokumentasjonen i stedet for til det som skal fjernes.
     */
    @Test
    fun `peker på modifikatorlinja, ikke på KDoc-en over den`() {
        val brudd = IngenInternalModifier.brudd(scope).single { linje -> linje.endsWith(": beregnDokumentert") }

        brudd shouldContain "Brudd.kt:15:"
    }

    /** Ren.kt dekker `private`, default-synlighet, `private set`, og navn og strenger som inneholder ordet «internal». */
    @Test
    fun `flagger ikke annen synlighet eller ordet internal i navn og tekst`() {
        val brudd = IngenInternalModifier.brudd(scope).filter { linje -> "Ren.kt" in linje }

        brudd.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier hopper over hele fila`() {
        shouldNotThrowAny {
            IngenInternalModifier.assert(scope, unntatteFilstier = setOf("internalmodifier/Brudd.kt"))
        }
    }

    @Test
    fun `assert kaster med lesbar melding ved brudd`() {
        val feil = shouldThrow<AssertionError> { IngenInternalModifier.assert(scope) }

        feil.message shouldContain "internal avgrenser til kompileringsmodulen"
        feil.message shouldContain "Fant 15 brudd"
    }
}
