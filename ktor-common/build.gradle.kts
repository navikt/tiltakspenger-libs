plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core.jvm)

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.server.core.jvm)
    // Server-bootstrap (embeddedServer/connector/Netty) for det felles oppstartsmønsteret.
    compileOnly(libs.ktor.server.host.common)
    compileOnly(libs.ktor.server.netty)

    testImplementation(project(":test-common"))
    testImplementation(libs.ktor.server.test.host)
    // Netty trengs i test for å låse Netty sin "event executor terminated"-streng (DefaultEventExecutor).
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.kotlinx.coroutines.test.jvm)
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
