val kafkaClientsVersion = "4.0.0"
val testcontainersVersion = "1.21.2"
val kotlinxCoroutinesVersion = "1.10.2"
dependencies {
    implementation(project(":logging"))

    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion")
    implementation("org.testcontainers:testcontainers:$testcontainersVersion")
    implementation("org.testcontainers:kafka:$testcontainersVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
}
