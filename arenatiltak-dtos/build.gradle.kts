plugins {
    id("tiltakspenger.bibliotek")
    id("tiltakspenger.dekning")
}

dependencies {
    implementation(project(":tiltak-dtos"))

    testImplementation(project(":test-common"))
}
