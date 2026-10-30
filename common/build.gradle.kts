plugins {
    id("no.nav.tiltakspenger.bibliotek")
}

dependencies {
    implementation(project(":logging"))
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.slf4j)
    implementation(libs.ulid.kotlin)
    implementation(libs.slf4j.api)

    testImplementation(project(":test-common"))
}
