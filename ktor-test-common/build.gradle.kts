plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":auth-test-core"))

    implementation(libs.arrow.core)
    implementation(libs.kotest.assertions.core)
    implementation(libs.kotest.assertions.json)

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.server.core.jvm)
    compileOnly(libs.ktor.server.test.host)
    compileOnly(libs.ktor.server.test.host.jvm)

    testImplementation(project(":test-common"))
    testImplementation(libs.ktor.server.test.host)
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
