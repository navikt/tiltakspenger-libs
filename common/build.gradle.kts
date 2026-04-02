plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":logging"))
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ulid.kotlin)
    implementation(libs.slf4j.api)

    testImplementation(project(":test-common"))
}
