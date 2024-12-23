dependencies {
    implementation(project(":periodisering"))
    implementation(project(":logging"))
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.aallam.ulid:ulid-kotlin:1.3.0")

    testImplementation(project(":test-common"))
}
