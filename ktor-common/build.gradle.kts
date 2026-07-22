plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))
    // Det felles oppstartsmønsteret bygger skedulerte jobber (GruppertTaskExecutor/TaskGruppe) og leader-election (RunCheckFactory) for konsumentene.
    // jobber er bevisst dependency-lett (ingen ktor/kafka), så koblingen drar ikke tunge avhengigheter inn i rene ktor-apper.
    implementation(project(":jobber"))

    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core.jvm)

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.server.core.jvm)
    // Server-bootstrap (embeddedServer/connector/Netty) for det felles oppstartsmønsteret.
    compileOnly(libs.ktor.server.host.common)
    compileOnly(libs.ktor.server.netty)
    // Ktor er bevisst låst til 3.4-linja (se dependabot.yml), så Netty-sikkerhetsfikser styres her i stedet.
    // api-scope med vilje: bom-en propagerer via modulmetadata og løfter også konsumentenes transitive Netty fra ktor-server-netty, uten å røre deres ktor-versjon.
    api(platform(libs.netty42.bom))

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
