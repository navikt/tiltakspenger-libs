dependencies {
    implementation(project(":common"))
    implementation("io.arrow-kt:arrow-core:1.2.4")

}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("personklient-domene")
}
