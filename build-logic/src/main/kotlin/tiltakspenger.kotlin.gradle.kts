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
