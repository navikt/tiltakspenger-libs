plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.arrow.core)
    implementation(libs.atomicfu)

    implementation(libs.ktor.utils)

    testImplementation(project(":test-common"))
}
