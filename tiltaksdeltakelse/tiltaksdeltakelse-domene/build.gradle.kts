plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
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
