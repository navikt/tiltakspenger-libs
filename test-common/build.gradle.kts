plugins {
    id("tiltakspenger-lib-conventions")
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
    }
    api(libs.kotlinx.coroutines.test.jvm)

    api(libs.logback.classic)
}
