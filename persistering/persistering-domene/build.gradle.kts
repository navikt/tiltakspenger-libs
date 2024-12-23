dependencies {
    implementation(project(":logging"))
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation(project(":test-common"))
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-domene")
}
