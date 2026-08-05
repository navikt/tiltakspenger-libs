plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    api(project(":common"))
    api(project(":json"))
    api(project(":logging"))
    api(project(":test-common"))

    api(libs.arrow.core)

    // Auth
    api(libs.auth0.java.jwt)
    api(libs.auth0.jwks.rsa)
}

// WireMock og Testcontainers bruker selv Apache HttpClient 5, og modulen eksponerer dem videre.
// Det er testinfrastruktur, ikke en klient vi kaller ut med, og den følger kun testscope videre til konsumentene — appenes egen runtimeClasspath er verifisert ren.
httpKlientGuard {
    tillat("org.apache.httpcomponents", "WireMock og Testcontainers bruker Apache HttpClient 5 internt; modulen er testinfrastruktur.")
}
