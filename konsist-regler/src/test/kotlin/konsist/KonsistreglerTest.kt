package no.nav.tiltakspenger.libs.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * Tester av det delte fundamentet i `Konsistregler.kt`, som alle reglene går via.
 */
internal class KonsistreglerTest {

    /**
     * Et git-arbeidstre lagt under repo-rota (`.worktrees/<gren>/`) plukkes opp av `Konsist.scopeFromProject()` som om det var en egen modul.
     * Da ville en regel som nettopp er skjerpet på hovedgrenen feilet på et arbeidstre som ennå ikke er rebaset, og blokkert hovedtreet for kode det ikke eier.
     */
    @Test
    fun `filer fra en annen utsjekk er ikke kildefiler`() {
        val scope = fixtureScope("utsjekker")

        // Scopet må faktisk se begge filene, ellers ville testen passert selv om fixturen aldri ble kopiert.
        scope.files.map { fil -> fil.name } shouldContainExactlyInAnyOrder listOf("Egen", "AnnenGren")

        scope.kildefiler().map { fil -> fil.name } shouldContainExactly listOf("Egen")
    }

    @Test
    fun `vakten slår ut først når skanningen fant færre enn minstekravet`() {
        assertSkanningenTraff(antall = 3, minstAntall = 3, hva = "filer")

        shouldThrow<AssertionError> {
            assertSkanningenTraff(antall = 2, minstAntall = 3, hva = "filer")
        }.message shouldContain "fant 2 filer"
    }

    /**
     * Ratchet-en sammenligner sti-suffikser mot bruddlinjene, som alltid er på formen `<filsti>:...`.
     * Begge formene modulen bruker er med her: med og uten linjenummer.
     */
    @Test
    fun `whitelistoppføringer uten et tilhørende brudd rapporteres`() {
        val brudd = listOf(
            "/repo/modul/src/main/kotlin/Fortsatt.kt:12: bruker noe forbudt",
            "/repo/modul/src/main/kotlin/OgsåFortsatt.kt: importerer noe forbudt",
        )

        assertWhitelistenErRyddet(setOf("kotlin/Fortsatt.kt", "kotlin/OgsåFortsatt.kt"), brudd)

        val feil = shouldThrow<AssertionError> {
            assertWhitelistenErRyddet(setOf("kotlin/Fortsatt.kt", "kotlin/Ryddet.kt", "kotlin/Feilstavet.kts"), brudd)
        }
        feil.message shouldContain "kotlin/Ryddet.kt"
        feil.message shouldContain "kotlin/Feilstavet.kts"
        feil.message shouldNotContain "kotlin/Fortsatt.kt"
    }

    /** Et filnavn som er suffiks av et annet skal ikke kvittere for det — matchingen er den samme som regelenes egen `endsWith`. */
    @Test
    fun `en whitelistoppføring kvitteres kun av sitt eget brudd`() {
        shouldThrow<AssertionError> {
            assertWhitelistenErRyddet(
                setOf("kotlin/Test.kt"),
                listOf("/repo/modul/src/test/kotlin/AnnenTest.kt:4: bruker noe forbudt"),
            )
        }.message shouldContain "kotlin/Test.kt"
    }
}
