/**
 * Konvensjonen for en publisert libs-modul: grunnoppsettet fra `tiltakspenger.kotlin`, pluss bibliotek-API og publisering.
 */

plugins {
    id("tiltakspenger.kotlin")
    id("tiltakspenger.publisering")
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
