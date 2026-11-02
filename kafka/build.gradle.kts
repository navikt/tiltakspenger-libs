plugins {
    id("no.nav.tiltakspenger.bibliotek")
}

dependencies {
    implementation(project(":logging"))

    // Løfter lz4-java og snappy-java over versjonene kafka-clients drar inn, se begrunnelsen i versjonskatalogen.
    // Constraintene publiseres i Gradle-modulmetadataen, så konsumentene arver dem ved neste libs-release.
    constraints {
        api(libs.lz4.java)
        api(libs.snappy.java)
    }

    api(libs.kafka.clients)
    api(libs.kotlinx.coroutines.core)
    api(libs.micrometer.core)

    testImplementation(project(":test-common"))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.micrometer.registry.prometheus)
}
