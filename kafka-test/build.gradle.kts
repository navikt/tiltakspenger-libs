plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    implementation(project(":logging"))

    // Samme løft som i :kafka. Modulen deklarerer kafka-clients selv og arver derfor ikke constrainten derfra.
    constraints {
        implementation(libs.lz4.java)
    }

    implementation(libs.kafka.clients)
    implementation(libs.testcontainers)
    implementation(libs.testcontainers.kafka)
}
