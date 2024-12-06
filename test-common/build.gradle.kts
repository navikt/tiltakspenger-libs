val kotestVersion = "5.9.1"
val mockkVersion = "1.13.13"
dependencies {
    api(project(":common"))
    api(project(":persistering:persistering-domene"))

    api("io.arrow-kt:arrow-core:2.0.0")

    api("io.kotest:kotest-assertions-core:$kotestVersion")
    api("io.kotest:kotest-assertions-json:$kotestVersion")
    api("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    api("io.kotest:kotest-extensions:$kotestVersion")

    api(platform("org.junit:junit-bom:5.11.3"))
    api("org.junit.jupiter:junit-jupiter")

    api("io.mockk:mockk:$mockkVersion")
    api("io.mockk:mockk-dsl-jvm:$mockkVersion")

    api("org.wiremock:wiremock:3.10.0")
    api("com.marcinziolo:kotlin-wiremock:2.1.1")
    api("io.kotest.extensions:kotest-extensions-wiremock:3.1.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.9.0")

    api("ch.qos.logback:logback-classic:1.5.12")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
