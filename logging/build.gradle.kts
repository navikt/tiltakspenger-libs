dependencies {
    api("io.github.microutils:kotlin-logging-jvm:3.0.5")

    testImplementation(project(":test-common"))
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
