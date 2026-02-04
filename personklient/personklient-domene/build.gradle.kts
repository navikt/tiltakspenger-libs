dependencies {
    implementation(project(":common"))
    implementation(project(":person-dtos"))
    implementation("io.arrow-kt:arrow-core:2.2.1.1")
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-domene")
}
