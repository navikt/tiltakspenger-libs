val jacksonVersion = "2.17.2"
val mockkVersion = "1.13.12"
val kotestVersion = "5.9.1"
val testContainersVersion = "1.20.1"

dependencies {
    implementation(project(":persistering:domene"))
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.github.seratch:kotliquery:1.9.0")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.mockk:mockk-dsl-jvm:$mockkVersion")

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    testImplementation("io.kotest:kotest-extensions:$kotestVersion")

    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    // need quarkus-junit-4-mock because of https://github.com/testcontainers/testcontainers-java/issues/970
    testImplementation("io.quarkus:quarkus-junit4-mock:3.13.2")

    testImplementation("org.postgresql:postgresql:42.7.3")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-infrastruktur")
}
