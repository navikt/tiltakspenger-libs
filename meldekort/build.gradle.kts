dependencies {
    implementation(project(":common"))

}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
