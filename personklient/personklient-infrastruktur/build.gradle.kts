dependencies {
    implementation(project(":person-dtos"))
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":common"))
    implementation(project(":personklient:personklient-domene"))

    implementation("io.arrow-kt:arrow-core:2.2.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    testImplementation(project(":test-common"))
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-infrastruktur")
}
