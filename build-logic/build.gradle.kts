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

// Samme reproduserbarhet som `tiltakspenger.kotlin` gir modulene; build-logic bruker ikke sin egen konvensjon.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
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
