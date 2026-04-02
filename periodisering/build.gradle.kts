plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":common"))

    implementation(libs.arrow.core)

    testImplementation(project(":test-common"))
}
