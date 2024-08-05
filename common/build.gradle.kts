dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.aallam.ulid:ulid-kotlin:1.3.0")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
