dependencies {
    implementation(project(":logging"))

    api(libs.kafka.clients)
    api(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-common"))
    testImplementation(project(":kafka-test"))
}
