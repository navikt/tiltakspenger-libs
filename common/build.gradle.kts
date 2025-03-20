dependencies {
    implementation(project(":periodisering"))
    implementation(project(":logging"))
    implementation("io.arrow-kt:arrow-core:2.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.aallam.ulid:ulid-kotlin:1.3.0")
    implementation("org.slf4j:slf4j-api:2.0.17")

    testImplementation(project(":test-common"))
}
