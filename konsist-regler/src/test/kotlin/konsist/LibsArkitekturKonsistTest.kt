package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Kjører de delte reglene på hele tiltakspenger-libs.
 * Konsist `scopeFromProject()`/`scopeFromTest()` skanner alle moduler, så disse testene dekker hele repoet.
 */
internal class LibsArkitekturKonsistTest {
    @Test
    fun `all kildekode bruker Jackson 3, ikke Jackson 2`() {
        IngenJackson2.assert(Konsist.scopeFromProject())
    }

    @Test
    fun `all testkode bruker JUnit 5, ikke JUnit 4`() {
        IngenJUnit4.assert(Konsist.scopeFromTest())
    }

    @Test
    fun `all testkode bruker Kotest assertions, ikke Jupiter Assertions`() {
        IngenJupiterAsserts.assert(Konsist.scopeFromTest())
    }

    @Test
    fun `ingen lokale Jackson-mappere utenfor json-modulen`() {
        IngenLokaleJacksonMappere.assert(Konsist.scopeFromProject())
    }

    @Test
    fun `henter aldri nåtid uten Clock`() {
        IngenNowUtenClock.assert(Konsist.scopeFromProject())
    }

    @Test
    fun `bruk nå fra libs-common, ikke LocalDateTime-now`() {
        IngenLocalDateTimeNow.assert(
            scope = Konsist.scopeFromProject(),
            unntatteFilstier = setOf("common/src/main/kotlin/common/LocalDateTimeEx.kt"),
        )
    }

    /**
     * Test-hjelpemodulene (`test-common`, `ktor-test-common`, `persistering-test-common`) er unntatt:
     * de skal per AGENTS-regelen «Ingen standardverdier» nettopp tilby `fixedClock`/`TikkendeKlokke` som teststandard.
     */
    @Test
    fun `Clock-parametre har ikke default-verdi i produksjonskode`() {
        IngenClockDefault.assert(Konsist.scopeFromProduction().slice { file -> "test-common" !in file.path })
    }

    /**
     * `httpklient`-infrastrukturen er unntatt fordi transporten selv er bygget på JDK-klienten (`java.net.http`).
     * `ktor-test-common` er unntatt fordi `defaultRequest` bruker `testApplication` sin ktor-klient — eneste vei inn til test-serveren, og ikke noe httpklient kan erstatte.
     */
    @Test
    fun `ingen andre http-klienter enn libs httpklient i produksjonskode`() {
        IngenAndreHttpKlienter.assert(
            Konsist.scopeFromProduction().slice { file ->
                "httpklient/httpklient-infrastruktur/" !in file.path && "ktor-test-common/" !in file.path
            },
        )
    }

    @Test
    fun `kdoc og kommentarer har maks en setning per linje`() {
        EnSetningPerLinje.assertFlereSetningerIKommentarer(Konsist.scopeFromProject())
    }

    @Test
    fun `kdoc og kommentarer brekker ikke en setning over flere linjer`() {
        EnSetningPerLinje.assertBrukneSetningerIKommentarer(Konsist.scopeFromProject())
    }

    @Test
    fun `markdown-filer har maks en setning per linje`() {
        EnSetningPerLinje.assertFlereSetningerIMarkdown(repoRot())
    }

    @Test
    fun `markdown-filer brekker ikke en setning over flere linjer`() {
        EnSetningPerLinje.assertBrukneSetningerIMarkdown(repoRot())
    }

    @Test
    fun `domene-moduler importerer ikke infra`() {
        InfraImport.assert(domeneModulScope(), infraSegmenter = setOf("infra", "infrastruktur"))
    }

    @Test
    fun `domene-moduler importerer kun tillatte pakker`() {
        DomeneImportWhitelist.assert(
            scope = domeneModulScope(),
            erDomenepakke = { true },
            tillattePakker = listOf(
                "arrow.core",
                "arrow.resilience",
                "io.github.oshai.kotlinlogging",
                "java.time",
                "java.util",
                "kotlin",
                "no.nav.tiltakspenger.libs.common",
                "no.nav.tiltakspenger.libs.httpklient",
                "no.nav.tiltakspenger.libs.logging",
                "no.nav.tiltakspenger.libs.persistering.domene",
                "no.nav.tiltakspenger.libs.person",
                "no.nav.tiltakspenger.libs.personklient",
                "no.nav.tiltakspenger.libs.tiltaksdeltakelse",
            ),
            infraSegmenter = setOf("infra", "infrastruktur"),
        )
    }

    /**
     * Produksjonskoden i domene-modulene (`*-domene`) — den skal være ren og uten infrastruktur-avhengigheter.
     * Testkildene deres er utenfor: tester bruker legitimt kotest/mockk/JUnit.
     * [BoundaryKlasser] kjøres bevisst ikke i dette repoet: `*-dtos`-modulene publiserer kontraktstyper som selve leveransen, så DTO-er utenfor infra-pakker er by design her.
     */
    private fun domeneModulScope() = Konsist.scopeFromProduction().slice { file -> "-domene/" in file.path }

    /** Testene kjører med arbeidskatalog i konsist-regler-modulen; repo-rota er katalogen over. */
    private fun repoRot(): Path = Path.of(System.getProperty("user.dir")).parent
}
