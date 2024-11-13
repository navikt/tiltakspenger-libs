dependencies {
    implementation(project(":common"))
    implementation(project(":json"))
    implementation(project(":periodisering"))
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
