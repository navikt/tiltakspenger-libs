plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    api(libs.arrow.core)

    implementation(libs.kotlin.logging.jvm)
    // Kun brukt av LokalDatabaseModus.Testcontainers-grenen.
    implementation(libs.testcontainers.postgresql)

    testImplementation(project(":test-common"))
}
