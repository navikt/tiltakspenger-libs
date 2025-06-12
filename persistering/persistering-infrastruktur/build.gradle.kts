val testContainersVersion = "1.21.1"

dependencies {
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":persistering:persistering-domene"))
    implementation("io.arrow-kt:arrow-core:2.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.seratch:kotliquery:1.9.1")

    testImplementation(project(":test-common"))

    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    // need quarkus-junit-4-mock because of https://github.com/testcontainers/testcontainers-java/issues/970
    testImplementation("io.quarkus:quarkus-junit4-mock:3.23.3")

    testImplementation("org.postgresql:postgresql:42.7.7")
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-infrastruktur")
}
