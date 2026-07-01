plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.arrow.core)
    implementation(libs.atomicfu)

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
