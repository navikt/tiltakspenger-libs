/**
 * Konvensjonen for en publisert libs-modul: grunnoppsettet fra `tiltakspenger.kotlin`, pluss bibliotek-API og publisering til GitHub Packages.
 * Versjonen settes av publiseringsworkflowen med `-Pversion=<UTC-tidsstempel>`; lokalt faller den tilbake på `version` i `gradle.properties`.
 */

plugins {
    id("tiltakspenger.kotlin")
    `java-library`
    `maven-publish`
}

group = "com.github.navikt.tiltakspenger-libs"

java {
    withSourcesJar()
}

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
