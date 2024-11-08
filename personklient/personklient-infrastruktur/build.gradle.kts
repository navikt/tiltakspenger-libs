dependencies {
    implementation(project(":person-dtos"))
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":common"))
    implementation(project(":personklient:personklient-domene"))

    implementation("io.arrow-kt:arrow-core:1.2.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(project(":test-common"))
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-infrastruktur")
}
