val testContainersVersion = "1.20.6"

dependencies {
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":persistering:persistering-domene"))
    implementation("io.arrow-kt:arrow-core:2.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.github.seratch:kotliquery:1.9.1")

    testImplementation(project(":test-common"))

    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    // need quarkus-junit-4-mock because of https://github.com/testcontainers/testcontainers-java/issues/970
    testImplementation("io.quarkus:quarkus-junit4-mock:3.21.0")

    testImplementation("org.postgresql:postgresql:42.7.5")
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-infrastruktur")
}
