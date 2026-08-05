plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    implementation(project(":common"))

    implementation(libs.arrow.core)

    testImplementation(project(":test-common"))
}
