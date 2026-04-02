plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":periodisering"))
    implementation(libs.ulid.kotlin)

    testImplementation(project(":test-common"))
}
