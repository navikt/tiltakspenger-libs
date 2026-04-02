plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    // Json
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.core)
    api(libs.jackson.module.kotlin)
    implementation(libs.arrow.core)
    implementation(libs.arrow.core.jackson)

    testImplementation(project(":test-common"))
}
