plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))
    // httpklient er api fordi HttpTransport og HttpKlientError er del av LeaderPodLookupClient sitt public API.
    api(project(":httpklient"))

    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.arrow.core)
    implementation(libs.atomicfu)

    testImplementation(project(":test-common"))
    testImplementation(testFixtures(project(":httpklient")))
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
