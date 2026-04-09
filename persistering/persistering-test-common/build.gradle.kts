plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    api(project(":persistering:persistering-infrastruktur"))
    api(project(":persistering:persistering-domene"))
    api(project(":test-common"))
    api(project(":logging"))
    api(project(":json"))

    implementation(libs.kotest.assertions.core)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotliquery)
    implementation(libs.hikaricp)
    implementation(libs.flyway.database.postgresql)

    implementation(libs.testcontainers)
    implementation(libs.testcontainers.junit.jupiter)
    implementation(libs.testcontainers.postgresql)

    implementation(libs.postgresql)
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-test-common")
}

