/**
 * Felles publiseringsoppsett for alt libs sender til GitHub Packages: bibliotekmodulene, versjonskatalogen og plattformen.
 * Selve publikasjonen deklareres av den som bruker pluginen, siden komponenten er ulik per artefakttype.
 *
 * Versjonen settes av publiseringsworkflowen med `-Pversion=<UTC-tidsstempel>`, som Gradle mapper til `project.version`.
 * Lokalt faller den tilbake på `version` i `gradle.properties`.
 */

plugins {
    `maven-publish`
}

group = "com.github.navikt.tiltakspenger-libs"

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
