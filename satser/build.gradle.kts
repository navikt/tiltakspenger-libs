plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":periodisering"))

    testImplementation(project(":test-common"))
}
