val kotlinxCoroutinesVersion = "1.10.1"
val kafkaClientsVersion = "3.9.0"
dependencies {
    implementation(project(":logging"))

    api("org.apache.kafka:kafka-clients:$kafkaClientsVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    testImplementation(project(":test-common"))
    testImplementation(project(":kafka-test"))
}
