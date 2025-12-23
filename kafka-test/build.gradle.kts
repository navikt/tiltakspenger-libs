val kafkaClientsVersion = "4.1.1"
val testContainersVersion = "2.0.3"
val kotlinxCoroutinesVersion = "1.10.2"
dependencies {
    implementation(project(":logging"))

    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion")
    implementation("org.testcontainers:testcontainers:$testContainersVersion")
    implementation("org.testcontainers:testcontainers-kafka:$testContainersVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
}
