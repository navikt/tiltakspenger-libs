val kotestVersion = "5.9.1"
val mockkVersion = "1.14.4"
dependencies {
    api(project(":common"))
    api(project(":persistering:persistering-domene"))

    api("io.arrow-kt:arrow-core:2.1.2")

    api("io.kotest:kotest-assertions-core:$kotestVersion")
    api("io.kotest:kotest-assertions-json:$kotestVersion")
    api("io.kotest:kotest-extensions:$kotestVersion")

    api(platform("org.junit:junit-bom:5.13.3"))
    api("org.junit.jupiter:junit-jupiter")
    api("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    api("io.mockk:mockk:$mockkVersion")
    api("io.mockk:mockk-dsl-jvm:$mockkVersion")

    api("org.wiremock:wiremock:3.13.1")
    api("com.marcinziolo:kotlin-wiremock:2.1.1")
    api("io.kotest.extensions:kotest-extensions-wiremock:3.1.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.2")

    api("ch.qos.logback:logback-classic:1.5.18")
}
