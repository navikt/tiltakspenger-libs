plugins {
    id("no.nav.tiltakspenger.bibliotek")
}

dependencies {
    implementation(project(":periodisering"))

    testImplementation(project(":test-common"))
}
