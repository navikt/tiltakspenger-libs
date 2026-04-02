dependencies {
    api(project(":common"))
    api(project(":persistering:persistering-domene"))

    api(libs.arrow.core)

    api(libs.kotest.assertions.core)
    api(libs.kotest.assertions.json)
    api(libs.kotest.extensions)

    api(platform(libs.junit.bom))
    api("org.junit.jupiter:junit-jupiter")
    api("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    api(libs.mockk)
    api(libs.mockk.dsl.jvm)

    api(libs.wiremock)
    api(libs.kotlin.wiremock)
    api(libs.kotest.extensions.wiremock)
    api(libs.kotlinx.coroutines.test.jvm)

    api(libs.logback.classic)
}
