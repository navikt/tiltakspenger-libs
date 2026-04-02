plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":json"))
    implementation(project(":periodisering"))

    testImplementation(project(":test-common"))
}
