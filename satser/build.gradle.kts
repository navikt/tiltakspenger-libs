plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    implementation(project(":periodisering"))

    testImplementation(project(":test-common"))
}
