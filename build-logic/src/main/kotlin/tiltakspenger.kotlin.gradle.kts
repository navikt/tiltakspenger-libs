import no.nav.tiltakspenger.byggelogikk.HttpKlientGuard
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

/**
 * Grunnkonvensjonen for alle Kotlin/JVM-moduler i tiltakspenger: toolchain, formatering, testoppsett og HTTP-klientgaten.
 * Pluginen sier ingenting om hva modulen er — et bibliotek legger `tiltakspenger.bibliotek` på toppen, en applikasjon sin egen plugin.
 */

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// JUnit 4 og vintage-motoren skal aldri på classpathen; JUnit 5 er den eneste testplattformen vi kjører.
// Kun `*Classpath`-konfigurasjonene treffes, slik at verktøyenes egne detached configurations (bl.a. spotless sin ktlint-provisjonering) står urørt.
configurations.matching { it.name.endsWith("Classpath") }.configureEach {
    exclude(group = "junit", module = "junit")
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
}

// Merk: spotless/ktlint har en flaky InvocationTargetException når taskene kjører parallelt over mange moduler.
// Konvensjonspluginen kan ikke serialisere dem pålitelig via en Gradle build service, så lint-skriptene kjører
// `spotlessApply` med `--no-parallel --max-workers=1` i stedet. Se `clean_lint_and_build.sh` og `lint_and_build.sh`.
spotless {
    kotlin {
        // Spotless henter default-målet sitt fra `SourceSet.allSource`, som også inneholder resources.
        // `.kt`-filer under resources er fixturer — input til Konsist-reglene, ikke kildekode — og
        // innholdet er poenget: `no-unused-imports` ville strippet nettopp importene bruddfixturene
        // skal bli tatt på. Reglene filtrerer bort de samme filene, se `Fixturer.kt`.
        targetExclude("**/resources/**")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_max-line-length" to "off",
                    // Fjerner ubrukte importer automatisk i spotlessApply, og feiler i spotlessCheck.
                    // Eksplisitt aktivert fordi default code style (intellij_idea) deaktiverer den.
                    "ktlint_standard_no-unused-imports" to "enabled",
                    "ktlint_standard_function-signature" to "disabled",
                    "ktlint_standard_function-expression-body" to "disabled",
                ),
            )
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // https://phauer.com/2018/best-practices-unit-testing-kotlin/
    systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
    testLogging {
        // Vi logger bare feilede og hoppede tester når Gradle kjører.
        events("skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

// Reproduserbare arkiver: uten dette skriver Gradle byggetidspunktet inn i hver zip-oppføring og lar
// filrekkefølgen følge filsystemet, slik at to bygg av samme kildekode gir ulik SHA-256.
// Da kan ingen sjekke at artefaktet i registeret er det som ble bygget fra taggen — attestasjonen binder
// en digest, men digesten kan ikke gjenskapes. Med dette normaliseres begge, og hvem som helst kan bygge
// på nytt fra commiten og sammenligne bytes.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// --- Ingen andre HTTP-klienter enn libs sin httpklient ---------------------------
// Se KDoc-en på HttpKlientGuard for hvorfor gaten finnes og hvorfor ktor-klienten ikke står på lista.
// Unntak deklareres i modulen selv med `httpKlientGuard { tillat("<koordinatprefiks>", "<begrunnelse>") }`.
val httpKlientGuard = extensions.create<HttpKlientGuard>("httpKlientGuard")

val verifiserHttpKlienter =
    tasks.register("verifiserHttpKlienter") {
        group = "verification"
        description = "Feiler hvis en annen HTTP-klient enn libs sin httpklient ligger på runtime-classpathen."
        // Verdiene hentes ut før doLast: configuration cache kan ikke serialisere referanser til byggskript-objekter.
        val forbudteKlienter = HttpKlientGuard.forbudteKlienter
        val tillatteKlienter = httpKlientGuard.tillatte
        val artefakter = configurations.named("runtimeClasspath").get().incoming.artifacts
        // Filene som input gir Gradle task-avhengighetene: uten dem kan ikke artefaktene slås opp
        // før jar-taskene til et inkludert bygg har kjørt (composite build).
        inputs.files(artefakter.artifactFiles).withPropertyName("runtimeClasspath")
        val runtimeKomponenter =
            artefakter.resolvedArtifacts
                .map { liste -> liste.map { artefakt -> artefakt.id.componentIdentifier.displayName } }
        doLast {
            val tillatt = tillatteKlienter.get().keys
            val forbudt = forbudteKlienter.filterNot { klient -> klient in tillatt }
            val funn = runtimeKomponenter.get().filter { komponent -> forbudt.any { it in komponent } }
            if (funn.isNotEmpty()) {
                throw GradleException(
                    "Andre HTTP-klienter enn libs sin httpklient på runtime-classpathen:\n" +
                        funn.distinct().sorted().joinToString("\n") { "- $it" },
                )
            }
        }
    }

tasks.named("check") { dependsOn(verifiserHttpKlienter) }

// --- Attestasjon på libs-artefaktene -------------------------------------------
// Skriver ut hvilke tiltakspenger-libs-artefakter som faktisk ligger på classpathen, slik at CI kan verifisere
// at hver enkelt kommer fra libs' publiseringsworkflow (`gh attestation verify --signer-workflow ...`).
//
// Tasken gjør bevisst ingen nettverkskall og henges ikke på `check`: verifiseringen koster et API-kall per
// artefakt og hører hjemme i CI, ikke i hvert lokale bygg. Den skriver kun stier — `gh` gjør selve jobben.
//
// Grunnen til at Gradle må peke dem ut, og ikke et `find` i Gradle-cachen: cachen samler opp alle versjoner
// som noen gang er lastet ned - flere hundre på en utviklermaskin - mens bygget bruker én av dem.
tasks.register("skrivLibsArtefakter") {
    group = "verification"
    description = "Skriver stiene til tiltakspenger-libs-artefaktene på runtime- og test-classpathen, for attestasjonssjekk i CI."
    val utfil = layout.buildDirectory.file("reports/libs-artefakter.txt")
    outputs.file(utfil)
    // Compile-variantene er med fordi en `compileOnly`-avhengighet aldri havner på runtime-classpathen,
    // men like fullt er kode vi bygger mot.
    val classpaths =
        listOf("runtimeClasspath", "testRuntimeClasspath", "compileClasspath", "testCompileClasspath")
            .filter { navn -> configurations.findByName(navn) != null }
            .map { navn -> configurations.named(navn).get().incoming.artifacts }
    classpaths.forEachIndexed { indeks, artefakter -> inputs.files(artefakter.artifactFiles).withPropertyName("classpath$indeks") }
    val filer =
        providers.provider {
            classpaths
                .flatMap { artefakter -> artefakter.resolvedArtifacts.get() }
                .filter { artefakt -> "com.github.navikt.tiltakspenger-libs" in artefakt.id.componentIdentifier.displayName }
                .map { artefakt -> artefakt.file.absolutePath }
                .distinct()
                .sorted()
        }
    doLast {
        val mål = utfil.get().asFile
        mål.parentFile.mkdirs()
        mål.writeText(filer.get().joinToString("\n", postfix = "\n"))
    }
}
