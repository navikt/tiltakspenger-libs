dependencies {
    implementation(project(":json"))
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
