dependencies {
    implementation(project(":common"))
    implementation(project(":person-dtos"))
    implementation("io.arrow-kt:arrow-core:2.0.0")
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-domene")
}
