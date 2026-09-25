package no.nav.tiltakspenger

/**
 * Konvensjonen for en publisert libs-modul: grunnoppsettet fra `no.nav.tiltakspenger.kotlin`, pluss bibliotek-API og publisering.
 */

plugins {
    id("no.nav.tiltakspenger.kotlin")
    id("no.nav.tiltakspenger.publisering")
    `java-library`
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name
            from(components["java"])
        }
    }
}
