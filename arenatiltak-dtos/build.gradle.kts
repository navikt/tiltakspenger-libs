plugins {
    id("no.nav.tiltakspenger.bibliotek")
    id("no.nav.tiltakspenger.dekning")
}

dependencies {
    implementation(project(":tiltak-dtos"))

    testImplementation(project(":test-common"))
}
