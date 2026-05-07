plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    api(libs.arrow.core)
    api(libs.arrow.resilience)
    api(project(":common"))
    api(project(":logging"))

    implementation(project(":json"))
    implementation(libs.kotlinx.coroutines.core)

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

tasks.check {
    dependsOn(tasks.koverVerify)
}
