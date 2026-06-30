plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":common"))

    testImplementation(project(":test-common"))
}

tasks.withType<Jar> {
    archiveBaseName.set("tiltaksdeltakelse-infrastruktur")
}
