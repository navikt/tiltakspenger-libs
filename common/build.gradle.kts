dependencies {
    implementation(project(":periodisering"))
    implementation(project(":logging"))
    implementation("io.arrow-kt:arrow-core:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.aallam.ulid:ulid-kotlin:1.3.0")

    testImplementation(project(":test-common"))
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
