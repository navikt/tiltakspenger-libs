val jacksonVersion = "2.17.2"
val mockkVersion = "1.13.12"
val kotestVersion = "5.9.1"

dependencies {
    implementation(project(":person-dtos"))

    implementation("io.arrow-kt:arrow-core:1.2.4")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation(project(":common"))
    implementation(project(":personklient:personklient-domene"))

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.mockk:mockk-dsl-jvm:$mockkVersion")

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    testImplementation("io.kotest:kotest-extensions:$kotestVersion")

    testImplementation("org.wiremock:wiremock:3.9.1")
    testImplementation("com.marcinziolo:kotlin-wiremock:2.1.1")
    testImplementation("io.kotest.extensions:kotest-extensions-wiremock:3.1.0")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.9.0")
    testImplementation(project(":test-common"))
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-infrastruktur")
}
