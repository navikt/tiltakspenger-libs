val jacksonVersion = "2.17.1"
val mockkVersion = "1.13.8"
val kotestVersion = "5.9.1"

dependencies {
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation(project(":person-dtos"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.mockk:mockk-dsl-jvm:$mockkVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")

    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    testImplementation("io.kotest:kotest-extensions:$kotestVersion")
    testImplementation("org.wiremock:wiremock:3.8.0")
    testImplementation("com.marcinziolo:kotlin-wiremock:2.1.1")
    testImplementation("io.kotest.extensions:kotest-extensions-wiremock:3.1.0")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.8.1")

}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
