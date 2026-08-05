plugins {
    id("tiltakspenger.bibliotek")
    id("tiltakspenger.dekning")
}

dependencies {
    implementation(project(":json"))
    implementation(project(":periodisering"))

    testImplementation(project(":test-common"))
}

