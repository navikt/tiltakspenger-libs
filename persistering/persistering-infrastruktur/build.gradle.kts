plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":persistering:persistering-domene"))
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotliquery)

    testImplementation(project(":test-common"))

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)

    testImplementation(libs.postgresql)
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-infrastruktur")
}
