plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":periodisering"))
    implementation(libs.ulid.kotlin)

    testImplementation(project(":test-common"))
}
