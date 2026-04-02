plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":logging"))

    implementation(libs.kafka.clients)
    implementation(libs.testcontainers)
    implementation(libs.testcontainers.kafka)
    implementation(libs.kotlinx.coroutines.core)
}
