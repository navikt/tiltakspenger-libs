dependencies {
    implementation("io.arrow-kt:arrow-core:1.2.4")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
