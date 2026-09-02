package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.Konsist
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Kjører de delte reglene på hele tiltakspenger-libs.
 * Konsist `scopeFromProject()`/`scopeFromTest()` skanner alle moduler, så disse testene dekker hele repoet.
 *
 * Seks av reglene kjøres bevisst ikke her.
 * [RouteBuilderKontrakt] gjelder ikke: libs har ingen route-test-buildere — ktor-test-common definerer hjelperne builderne bruker, ikke buildere.
 * [Testparallellitet] gjelder ikke: libs-modulene kjører ikke testene sine parallelt ennå.
 * [IsolertDatabasetestKonvensjon] gjelder ikke: ingen libs-tester bruker runIsolated — persistering-test-common definerer parameteren, konsumentene bruker den.
 * [IngenInternalModifier] gjelder motsatt vei: libs er det eneste repoet der `internal` faktisk avgrenser noe, siden modulene publiseres som artefakter.
 * [JsonbSkriving] gjelder ikke: libs skriver ingen SQL i produksjonskode — `persistering` tilbyr sesjons- og transaksjonshåndteringen, mens spørringene bor i konsumentene.
 * [DomenepakkeUtenInfrastruktur] gjelder ikke: libs skiller domene og infrastruktur i moduler, ikke i pakker under én domenepakke — [InfraImport] på `-domene/`-slicet er formen regelen tar her.
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

    /** Fila som definerer `nå(clock)` kaller `LocalDateTime.now(clock)` legitimt — den er hele poenget med hjelperen. */
    private val unntattLocalDateTimeNow = setOf("common/src/main/kotlin/common/LocalDateTimeEx.kt")

    @Test
    fun `bruk nå fra libs-common, ikke LocalDateTime-now`() {
        IngenLocalDateTimeNow.assert(
            scope = Konsist.scopeFromProject(),
            unntatteFilstier = unntattLocalDateTimeNow,
        )
    }

    @Test
    fun `whitelisten for LocalDateTime-now inneholder ingen ryddede filer`() {
        assertWhitelistenErRyddet(unntattLocalDateTimeNow, IngenLocalDateTimeNow.brudd(Konsist.scopeFromProject()))
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
        IngenAndreHttpKlienter.assertIngenKlienterIProduksjonskode(
            Konsist.scopeFromProduction().slice { file ->
                "httpklient/httpklient-infrastruktur/" !in file.path && "ktor-test-common/" !in file.path
            },
        )
    }

    /** De tre testene som med vilje kjører en ekte server over sokkel og trenger en klient utenfra: oppstartstestene i `ktor-common` og WireMock-hjelperen i `test-common`. */
    private val unntatteHttpKlienttester = setOf(
        "ktor-common/src/test/kotlin/ktor/common/oppstart/AppTest.kt",
        "ktor-common/src/test/kotlin/ktor/common/oppstart/OppstartTest.kt",
        "test-common/src/test/kotlin/common/WiremockExTest.kt",
    )

    /**
     * Testkoden får bruke `testApplication`-klienten, men ikke lage ekte nettverksklienter.
     * `httpklient`-infrastrukturen er unntatt av samme grunn som over — den tester sin egen JDK-transport.
     */
    @Test
    fun `ingen ekte http-klienter i testkode`() {
        IngenAndreHttpKlienter.assertIngenKlienterITestkode(
            httpKlientTestscope(),
            unntatteFilstier = unntatteHttpKlienttester,
        )
    }

    @Test
    fun `whitelisten for http-klienter i testkode inneholder ingen ryddede filer`() {
        assertWhitelistenErRyddet(unntatteHttpKlienttester, IngenAndreHttpKlienter.klienterITestkode(httpKlientTestscope()))
    }

    @Test
    fun `ingen andre http-klienter deklarert i byggfilene`() {
        IngenAndreHttpKlienter.assertIngenKlientavhengigheter(repoRot())
    }

    @Test
    fun `ingen ellevesifrede tall er hardkodet`() {
        IngenHardkodedeFnr.assert(repoRot())
    }

    @Test
    fun `backticks rundt navn kun for testnavn med mellomrom`() {
        IngenBackticksUtenMellomrom.assert(Konsist.scopeFromProject())
    }

    @Test
    fun `ingen global mocking i testkode`() {
        IngenGlobalMocking.assert(Konsist.scopeFromTest())
    }

    @Test
    fun `personopplysninger maskerer toString selv`() {
        PersonopplysningMaskererToString.assert(Konsist.scopeFromProduction())
    }

    @Test
    fun `ingen JUnit-livssyklus i testkode`() {
        IngenJUnitLivssyklus.assert(Konsist.scopeFromTest())
    }

    @Test
    fun `ingen muterbar tilstand i testklassers felter`() {
        IngenMuterbareTestfelter.assert(Konsist.scopeFromTest())
    }

    /**
     * `WiremockExTest` tester WireMock-hjelperne i test-common og er wire-format per definisjon.
     * Personklient-testene er migreringsgjeld: de kjører produksjonsklienten over WireMock i stedet for `FakeHttpTransport`, og står på whitelisten til noen migrerer dem.
     */
    private val tillatteWireMockTester = setOf(
        "test-common/src/test/kotlin/common/WiremockExTest.kt",
        "personklient/personklient-infrastruktur/src/test/kotlin/personklient/pdl/FellesHttpPersonklientTest.kt",
        "personklient/personklient-infrastruktur/src/test/kotlin/personklient/skjerming/FellesHttpSkjermingsklientTest.kt",
    )

    /**
     * `httpklient-infrastruktur` er unntatt: modulens tester ER wire-format-laget — de verifiserer den ekte JDK-transporten mot en levende server, som er nettopp det WireMock finnes for.
     */
    @Test
    fun `wiremock kun i bevisste wire-format-tester`() {
        WireMockKunForWireFormat.assert(httpKlientTestscope(), tillatteFiler = tillatteWireMockTester)
    }

    @Test
    fun `whitelisten for wiremock inneholder ingen ryddede filer`() {
        assertWhitelistenErRyddet(tillatteWireMockTester, WireMockKunForWireFormat.brudd(httpKlientTestscope()))
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
        InfraImport.assert(domeneModulScope(), ekstraInfraSegmenter = setOf("infrastruktur"))
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
                // java.net.URI er en ren JDK-verditype på linje med java.time: HttpKlientMetadata modellerer hvilken URI kallet gikk mot.
                // Bevisst kun URI og ikke hele java.net, som ville sluppet inn java.net.http-klienten.
                "java.net.URI",
                "java.time",
                "java.util",
                "kotlin",
                "no.nav.tiltakspenger.libs.common",
                "no.nav.tiltakspenger.libs.httpklient",
                "no.nav.tiltakspenger.libs.logging",
                "no.nav.tiltakspenger.libs.persistering.domene",
                "no.nav.tiltakspenger.libs.periode",
                "no.nav.tiltakspenger.libs.person",
                "no.nav.tiltakspenger.libs.personklient",
                "no.nav.tiltakspenger.libs.tiltaksdeltakelse",
            ),
            ekstraInfraSegmenter = setOf("infrastruktur"),
        )
    }

    /**
     * Produksjonskoden i domene-modulene (`*-domene`) — den skal være ren og uten infrastruktur-avhengigheter.
     * Testkildene deres er utenfor: tester bruker legitimt kotest/mockk/JUnit.
     * [BoundaryKlasser] kjøres bevisst ikke i dette repoet: `*-dtos`-modulene publiserer kontraktstyper som selve leveransen, så DTO-er utenfor infra-pakker er by design her.
     */
    private fun domeneModulScope() = Konsist.scopeFromProduction().slice { file -> "-domene/" in file.path }

    /** Testkoden utenom `httpklient-infrastruktur`, som tester sin egen transport og derfor er unntatt begge klientreglene. */
    private fun httpKlientTestscope() = Konsist.scopeFromTest().slice { file -> "httpklient/httpklient-infrastruktur/" !in file.path }

    /** Testene kjører med arbeidskatalog i konsist-regler-modulen; repo-rota er katalogen over. */
    private fun repoRot(): Path = Path.of(System.getProperty("user.dir")).parent
}
