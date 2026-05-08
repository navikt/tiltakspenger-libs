plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":tiltak-dtos"))

    testImplementation(project(":test-common"))
}
