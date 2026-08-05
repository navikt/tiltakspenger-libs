// Cyclonedx-pluginen i plugins-blokka under drar inn jackson 2 på build-logics EGEN buildscript-classpath.
// Det er en egen konfigurasjon som jackson2-bom-løftet i dependencies-blokka lenger ned ikke når - det løftet
// gjelder artefaktet konsumentene henter, mens dette gjelder bygget av build-logic selv (inkludert som del av
// rotbygget, og dermed med i avhengighetsgrafen repoet sender inn).
// Versjonene er skrevet ut fordi buildscript-blokka evalueres før katalog-accessorene finnes; hold dem i sync med `jackson2` i katalogen.
buildscript {
    dependencies {
        constraints {
            // Åpne advisories på 2.20.1 (bl.a. GHSA-j3rv-43j4-c7qm); samme mønster som avro-repoenes buildscript-pinning.
            add("classpath", "com.fasterxml.jackson.core:jackson-core:2.22.1")
            add("classpath", "com.fasterxml.jackson.core:jackson-databind:2.22.1")
        }
    }
}

plugins {
    `kotlin-dsl`
    `maven-publish`
    // Egen SBOM: build-logic er et eget bygg, og fanges derfor ikke av aggregatet i hovedbygget.
    alias(libs.plugins.cyclonedx)
}

// Kan ikke bruke `tiltakspenger.publisering` — den er definert her inne, og et bygg kan ikke applisere sine egne plugins.
// `kotlin-dsl` drar inn `java-gradle-plugin`, som sammen med `maven-publish` lager både hovedpublikasjonen
// og en markør per prekompilert skript-plugin, slik at app-repoene kan skrive `id("tiltakspenger.kotlin")`.
group = "com.github.navikt.tiltakspenger-libs"
version = providers.gradleProperty("version").getOrElse("0.0.0-lokal")

// Nekter å publisere en lokal utviklingsversjon: GitHub Packages er immutable, og et feilaktig
// versjonsnummer kan verken overskrives eller trekkes tilbake.
tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst {
        require(!project.version.toString().endsWith("-lokal")) {
            "build-logic må publiseres med -Pversion=<versjon>. Nekter å publisere ${project.version}."
        }
    }
}

// Samme reproduserbarhet som `tiltakspenger.kotlin` gir modulene; build-logic bruker ikke sin egen konvensjon.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Samme SBOM-konvensjoner som `tiltakspenger.sbom` gir modulene; build-logic kan ikke bruke sin egen plugin.
// Uten componentGroup får rotnoden en uoppslåelig `pkg:maven/unspecified/...`-purl, og uten avgrensningen
// beskriver SBOM-en også avhengigheter som aldri havner på konsumentens buildscript-classpath.
tasks.named<org.cyclonedx.gradle.BaseCyclonedxTask>("cyclonedxBom") {
    componentGroup = "com.github.navikt.tiltakspenger-libs"
    projectType = org.cyclonedx.model.Component.Type.LIBRARY
}
tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
    includeConfigs = listOf("runtimeClasspath")
}

publishing {
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

dependencies {
    // Runtime-classpathen her blir konsumentenes buildscript-classpath, og cyclonedx-pluginen drar inn
    // Jackson 2 med åpne advisories (databind/core 2.20.1, bl.a. GHSA-j3rv-43j4-c7qm). Bom-en løfter dem
    // over de patchede versjonene — samme grep som `plattform` gjør for app-classpathene.
    implementation(platform(libs.jackson2.bom))
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.cyclonedx.gradle.plugin)
    // Gjør versjonskatalogens type-sikre accessors (`libs.*`) tilgjengelige i de prekompilerte skript-pluginene.
    // NB: klassen genereres av *konsumentens* katalog. Et app-repo som tar i bruk disse pluginene må derfor
    // importere `versjonskatalog` under navnet `libs` — pluginene slår opp bl.a. `junit-bom`, `junit-jupiter`,
    // `junit-jupiter-params`, `junit-platform-launcher` og `versions.ktlint`.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
