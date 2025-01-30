val kotlinxCoroutinesVersion = "1.10.1"
dependencies {
    implementation(project(":common"))
    implementation(project(":json"))
    implementation(project(":logging"))

    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")

    // Auth
    api("com.auth0:java-jwt:4.5.0")
    api("com.auth0:jwks-rsa:0.22.1")

    testImplementation(project(":test-common"))
}
