plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    api(project(":common"))
    implementation(project(":json"))

    api(libs.arrow.core)

    api(libs.kotest.assertions.core)
    api(libs.kotest.assertions.json)
    api(libs.kotest.extensions)

    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    api(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)

    api(libs.mockk)
    api(libs.mockk.dsl.jvm)

    api(libs.wiremock)
    // Kotlin-wrapperne drar inn fat-jaren wiremock-standalone, som dupliserer WireMock-klassene og skjuler en shadet, utdatert Jetty for Dependabot.
    api(libs.kotlin.wiremock) {
        exclude(group = "org.wiremock", module = "wiremock-standalone")
    }
    api(libs.kotest.extensions.wiremock) {
        exclude(group = "org.wiremock", module = "wiremock-standalone")
    }
    // Løfter WireMocks transitive avhengigheter over de patchede versjonene; se begrunnelser i version catalog.
    api(platform(libs.jetty.bom))
    api(platform(libs.jetty.ee10.bom))
    api(platform(libs.jackson2.bom))
    constraints {
        // WireMock 3.13.2 drar inn handlebars 4.3.1 med path traversal (GHSA-r4gv-qr8j-p3pg, patchet i 4.5.2).
        api(libs.handlebars)
        // wiremock-jetty12 drar også inn httpclient5 5.5.1 med httpcore5 5.3.6, som har hullene plattform-BOM-en pinner bort for app-repoene
        // (CVE-2026-54399, CVE-2026-54428, CVE-2026-64607). BOM-en gjelder ikke libs' eget bygg, så modulen pinner selv.
        // `api` gjør at testmodulene som bygger på denne, og den publiserte POM-en, får samme versjon.
        api(libs.httpclient5)
        api(libs.httpcore5)
        api(libs.httpcore5.h2)
    }
    api(libs.kotlinx.coroutines.test.jvm)

    api(libs.logback.classic)
}

// WireMock og Testcontainers bruker selv Apache HttpClient 5, og modulen eksponerer dem videre.
// Det er testinfrastruktur, ikke en klient vi kaller ut med, og den følger kun testscope videre til konsumentene — appenes egen runtimeClasspath er verifisert ren.
httpKlientGuard {
    tillat("org.apache.httpcomponents", "WireMock og Testcontainers bruker Apache HttpClient 5 internt; modulen er testinfrastruktur.")
}
