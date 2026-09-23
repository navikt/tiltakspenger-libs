plugins {
    id("tiltakspenger.bibliotek")
    id("tiltakspenger.dekning")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":auth-test-core"))

    // api, ikke implementation: HttpMethod står i signaturen til defaultRequest, så konsumentene må se den.
    api(project(":httpklient:httpklient-infrastruktur"))

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

// WireMock og Testcontainers bruker selv Apache HttpClient 5, og modulen eksponerer dem videre.
// Det er testinfrastruktur, ikke en klient vi kaller ut med, og den følger kun testscope videre til konsumentene — appenes egen runtimeClasspath er verifisert ren.
httpKlientGuard {
    tillat("org.apache.httpcomponents", "WireMock og Testcontainers bruker Apache HttpClient 5 internt; modulen er testinfrastruktur.")
}
