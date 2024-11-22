val testContainersVersion = "1.20.4"

dependencies {
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":persistering:persistering-domene"))
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.github.seratch:kotliquery:1.9.0")

    testImplementation(project(":test-common"))

    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    // need quarkus-junit-4-mock because of https://github.com/testcontainers/testcontainers-java/issues/970
    testImplementation("io.quarkus:quarkus-junit4-mock:3.17.0")

    testImplementation("org.postgresql:postgresql:42.7.4")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-infrastruktur")
}
