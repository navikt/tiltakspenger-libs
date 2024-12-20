dependencies {
    implementation(project(":common"))
    implementation(project(":person-dtos"))
    implementation("io.arrow-kt:arrow-core:1.2.4")
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-domene")
}
