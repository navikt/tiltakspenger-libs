plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    // common er api: Virksomhetsnavn og Tilknytningstittel er del av den offentlige flaten.
    api(project(":common"))

    // periodisering er api: Periode er del av GirRett.MedPeriode sin offentlige flate.
    api(project(":periodisering"))

    testImplementation(project(":test-common"))
}

kover {
    reports {
        verify {
            rule {
                minBound(100)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("koverVerify")
}

tasks.withType<Jar> {
    archiveBaseName.set("tiltaksdeltakelse-domene")
}
