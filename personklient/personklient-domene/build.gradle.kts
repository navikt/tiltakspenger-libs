plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":person-dtos"))
    implementation(libs.arrow.core)
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-domene")
}
