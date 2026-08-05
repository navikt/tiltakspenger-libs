/**
 * Publiserer `gradle/libs.versions.toml` som en versjonskatalog app-repoene kan importere.
 *
 * Katalogen er ett av fire lag i det delte byggoppsettet, og det svakeste: den kan kun deklarere koordinater og versjoner.
 * Constraints som skal virke transitivt hører hjemme i `plattform`, og alt som krever `exclude` eller resolusjonsregler hører hjemme i convention-pluginene.
 *
 * Konsumeres i app-repoets `settings.gradle.kts`:
 * ```
 * dependencyResolutionManagement {
 *     versionCatalogs {
 *         create("libs") { from("com.github.navikt.tiltakspenger-libs:versjonskatalog:<versjon>") }
 *     }
 * }
 * ```
 */

plugins {
    `version-catalog`
    id("tiltakspenger.publisering")
}

// TODO jah: Gradle genererer den publiserte toml-en på nytt og stripper alle kommentarer.
// Sikkerhetsbegrunnelsene i katalogen — hvorfor netty, lz4, scram og jetty er pinnet, og hvorfor ktor er låst til 3.4-linja —
// følger derfor ikke med til konsumentene, som ser versjonene uten å se hvorfor de står der.
// Vurder å speile begrunnelsene i README, eller å la plattformen bære dem.
catalog {
    versionCatalog {
        from(files("../gradle/libs.versions.toml"))
    }
}

publishing {
    publications {
        create<MavenPublication>("versjonskatalog") {
            artifactId = project.name
            from(components["versionCatalog"])
        }
    }
}
