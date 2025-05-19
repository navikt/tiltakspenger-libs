dependencies {
    implementation(project(":common"))
    implementation(project(":person-dtos"))
    implementation("io.arrow-kt:arrow-core:2.1.2")
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-domene")
}
