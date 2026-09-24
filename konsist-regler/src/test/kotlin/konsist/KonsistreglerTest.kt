package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.Konsist
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tester av det delte fundamentet i `Konsistregler.kt`, som alle reglene går via.
 */
internal class KonsistreglerTest {

    /**
     * `Konsist.scopeFromProject()` tar med et git-arbeidstre under repo-rota (`.worktrees/<gren>/`) som om det var en egen modul.
     * En regel som er skjerpet på hovedgrenen ville da feilet på et arbeidstre som ikke er rebaset ennå.
     */
    @Test
    fun `filer fra en annen utsjekk er ikke kildefiler`() {
        val scope = fixtureScope("utsjekker")

        // Scopet må se begge filene, ellers ville testen passert selv om fixturen aldri ble kopiert.
        scope.files.map { fil -> fil.name } shouldContainExactlyInAnyOrder listOf("Egen", "AnnenGren")

        scope.kildefiler().map { fil -> fil.name } shouldContainExactly listOf("Egen")
    }

    /**
     * Kjøres bygget fra et arbeidstre, ligger `.worktrees` over rota, og filene skal bli med.
     * Med det gamle substring-søket hadde hver fil i treet substrengen `/.worktrees/`, scopet ble tømt, og reglene ble grønne uten å lese kode.
     */
    @Test
    fun `filene blir med når rota selv ligger i et arbeidstre`() {
        val arbeidstre = fixturesti("utsjekker").resolve(".worktrees/annen-gren")

        val scope = Konsist.scopeFromExternalDirectory(arbeidstre.toString())

        scope.kildefiler(arbeidstre.ekteSti()).map { fil -> fil.name } shouldContainExactly listOf("AnnenGren")
    }

    /** Samme filer, men med rota et hakk opp, der arbeidstreet ligger under rota og fortsatt skal ekskluderes. */
    @Test
    fun `et arbeidstre under rota ekskluderes fortsatt`() {
        val scope = fixtureScope("utsjekker")

        scope.kildefiler(fixturesti("utsjekker").ekteSti()).map { fil -> fil.name } shouldContainExactly listOf("Egen")
    }

    /**
     * Fjerner filteret alle filene i et scope som ikke var tomt, har reglene ingenting å lese.
     * Meldingen må peke på rota og en eksempelfil, ellers er den ikke til å feilsøke på.
     */
    @Test
    fun `et scope som filteret tømmer helt blir rødt`() {
        val scope = Konsist.scopeFromExternalDirectory(fixturesti("utsjekker").resolve(".worktrees/annen-gren").toString())

        val feil = shouldThrow<AssertionError> { scope.kildefiler(fixturesti("utsjekker").ekteSti()) }

        feil.message shouldContain "fjernet alle 1 filene"
        feil.message shouldContain "AnnenGren.kt"
    }

    /**
     * Et scope som var tomt fra før er noe annet enn at skanningen bommet på treet.
     * Et `slice { }` på en modul repoet ikke har, eller et testkildesett som ikke finnes ennå, er gyldige tilstander, og derfor trengs det ikke noe unntak på kallstedet.
     */
    @Test
    fun `et scope som var tomt fra før blir ikke rødt`() {
        fixtureScope("utsjekker").slice { false }.kildefiler().shouldBeEmpty()
    }

    /**
     * Ligger fila utenfor rota, som i et `scopeFromExternalDirectory`-scope, finnes det ingen rot å måle mot, og hele den absolutte stien brukes.
     * En katalog som ser ut som en annen utsjekk holdes da fortsatt utenfor.
     */
    @Test
    fun `en fil utenfor rota måles ikke mot den`() {
        val rot = Path.of("/repo")

        Path.of("/repo/modul/Fil.kt").settFra(rot) shouldBe Path.of("modul/Fil.kt")
        Path.of("/annet/sted/Fil.kt").settFra(rot) shouldBe Path.of("/annet/sted/Fil.kt")
    }

    /**
     * På macOS er `/tmp` en symlenke til `/private/tmp`.
     * Løses ikke begge sider opp, er rot og fil to ulike stier, og filteret faller tilbake på den absolutte stien.
     */
    @Test
    fun `symlenker løses opp slik at rot og fil kan sammenlignes`(@TempDir katalog: Path) {
        val ekte = Files.createDirectory(katalog.resolve("ekte"))
        val lenke = Files.createSymbolicLink(katalog.resolve("lenke"), ekte)
        Files.createFile(ekte.resolve("Fil.kt"))

        lenke.resolve("Fil.kt").ekteSti() shouldBe ekte.ekteSti().resolve("Fil.kt")
    }

    @Test
    fun `vakten slår ut først når skanningen fant færre enn minstekravet`() {
        assertSkanningenTraff(antall = 3, minstAntall = 3, hva = "filer")

        shouldThrow<AssertionError> {
            assertSkanningenTraff(antall = 2, minstAntall = 3, hva = "filer")
        }.message shouldContain "fant 2 filer"
    }

    /**
     * Sjekken sammenligner sti-suffikser mot bruddlinjene, som har formen `<filsti>:...`.
     * Begge formene modulen bruker er med her, med og uten linjenummer.
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

    /** Et filnavn som er suffiks av et annet skal ikke kvittere for det, siden matchingen er den samme som regelenes egen `endsWith`. */
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
