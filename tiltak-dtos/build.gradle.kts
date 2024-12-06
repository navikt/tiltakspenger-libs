dependencies {
    implementation("io.arrow-kt:arrow-core:2.0.0")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
