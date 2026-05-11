plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":json"))
    implementation(project(":periodisering"))

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

