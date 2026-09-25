plugins {
    id("no.nav.tiltakspenger.bibliotek")
    id("no.nav.tiltakspenger.dekning")
}

dependencies {
    implementation(project(":json"))
    implementation(project(":periodisering"))

    testImplementation(project(":test-common"))
}

