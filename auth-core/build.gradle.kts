val kotlinxCoroutinesVersion = "1.10.2"
dependencies {
    implementation(project(":common"))
    implementation(project(":json"))
    implementation(project(":logging"))

    implementation("io.arrow-kt:arrow-core:2.2.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    // Auth
    api("com.auth0:java-jwt:4.5.0")
    api("com.auth0:jwks-rsa:0.23.0")

    testImplementation(project(":test-common"))
}
