val testContainersVersion = "2.0.3"

dependencies {
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":persistering:persistering-domene"))
    implementation("io.arrow-kt:arrow-core:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.seratch:kotliquery:1.9.1")

    testImplementation(project(":test-common"))

    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testContainersVersion")

    testImplementation("org.postgresql:postgresql:42.7.8")
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-infrastruktur")
}
