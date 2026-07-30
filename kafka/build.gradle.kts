plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":logging"))

    // Løfter lz4-java over kafka-clients' egen 1.10.2, se begrunnelsen i versjonskatalogen.
    // Constrainten publiseres i Gradle-modulmetadataen, så konsumentene arver den ved neste libs-release.
    constraints {
        api(libs.lz4.java)
    }

    api(libs.kafka.clients)
    api(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-common"))
    testImplementation(project(":kafka-test"))
}
