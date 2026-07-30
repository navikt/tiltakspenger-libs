import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
    id("java-library")
    id("com.diffplug.spotless")
}

group = "com.github.navikt.tiltakspenger-libs"

repositories {
    mavenCentral()
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
configurations.matching { it.name.endsWith("Classpath") }.configureEach {
    exclude(group = "junit", module = "junit")
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
}
// Note: Spotless/ktlint has a flaky InvocationTargetException when its tasks run
// in parallel across many subprojects. The convention plugin can't reliably
// serialize them via a Gradle build service, so the project's lint scripts run
// `spotlessApply` with `--no-parallel --max-workers=1` instead.
// See `clean_lint_and_build.sh` and `lint_and_build.sh`.

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
tasks {
    kotlin {
        jvmToolchain(25)
        compilerOptions {
            freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
        }
    }
    test {
        // JUnit 5 support
        useJUnitPlatform()
        // https://phauer.com/2018/best-practices-unit-testing-kotlin/
        systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
        testLogging {
            // We only want to log failed and skipped tests when running Gradle.
            events("skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }
}
java {
    withSourcesJar()
}

// --- Ingen andre HTTP-klienter enn httpklient ---------------------------------
// Konsist-reglene (IngenAndreHttpKlienter) dekker det vi selv skriver og deklarerer.
// Denne dekker det siste hullet: en klient som kommer inn transitivt gjennom en annen
// avhengighet, uten at den står i noen import eller byggfil. Den ligger i convention-
// pluginen slik at alle modulene får den, og fordi en klient på runtime-classpathen til
// en libs-modul forplanter seg til alle konsumentene.
//
// Ktor-klienten står bevisst IKKE på lista, og skal ikke legges til: `ktor-server-auth`
// eksponerer `ktor-client-core` som `api` (OAuth-provideren bruker den), så den ligger på
// classpathen så lenge vi bruker ktor sin server-auth. Ktor-klienten håndheves i kilden
// (konsist-regelen) og i byggfila.
val verifiserHttpKlienter =
    tasks.register("verifiserHttpKlienter") {
        group = "verification"
        description = "Feiler hvis en annen HTTP-klient enn libs sin httpklient ligger på runtime-classpathen."
        // Lista ligger inne i tasken, ikke som script-val: configuration cache kan ikke
        // serialisere referanser til byggskript-objekter fanget i doLast.
        //
        // Test-hjelpemodulene eksponerer WireMock og Testcontainers, som selv bruker Apache
        // HttpClient 5. Det er testinfrastruktur, ikke en klient vi kaller ut med, og den følger
        // kun testscope videre til konsumentene — appenes egen runtimeClasspath er verifisert ren.
        // Derfor er kun Apache tatt ut for disse modulene; resten av lista gjelder også der.
        val erTesthjelpemodul = project.name in setOf("test-common", "auth-test-core", "ktor-test-common", "persistering-test-common")
        val forbudteHttpKlienter =
            listOfNotNull(
                "com.squareup.okhttp3",
                "com.squareup.retrofit2",
                "org.apache.httpcomponents".takeUnless { erTesthjelpemodul },
                "com.github.kittinunf.fuel",
                "com.konghq:unirest",
                "io.vertx:vertx-web-client",
                "org.http4k:http4k-client",
                "io.github.openfeign",
            )
        val artefakter = configurations.named("runtimeClasspath").get().incoming.artifacts
        // Filene som input gir Gradle task-avhengighetene: uten dem kan ikke artefaktene slås opp
        // før jar-taskene til et inkludert bygg har kjørt (composite build).
        inputs.files(artefakter.artifactFiles).withPropertyName("runtimeClasspath")
        val runtimeKomponenter =
            artefakter.resolvedArtifacts
                .map { liste -> liste.map { artefakt -> artefakt.id.componentIdentifier.displayName } }
        doLast {
            val funn = runtimeKomponenter.get().filter { komponent -> forbudteHttpKlienter.any { it in komponent } }
            if (funn.isNotEmpty()) {
                throw GradleException(
                    "Andre HTTP-klienter enn libs sin httpklient på runtime-classpathen:\n" +
                        funn.distinct().sorted().joinToString("\n") { "- $it" },
                )
            }
        }
    }

tasks.named("check") { dependsOn(verifiserHttpKlienter) }
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name
            version = project.findProperty("version")?.toString() ?: "0.0.0"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/navikt/tiltakspenger-libs")
            credentials {
                username = "x-access-token"
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
