plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(libs.jackson.annotations)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache5)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.jackson3)
    implementation(libs.ktor.server.auth)
    implementation(libs.arrow.core)
    implementation(project(":logging"))
    implementation(project(":common"))
    implementation(project(":json"))
    implementation(project(":ktor-common"))

    testImplementation(project(":test-common"))
    testImplementation(project(":ktor-test-common"))
    testImplementation(project(":auth-test-core"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.arrow.core.jackson)
}
