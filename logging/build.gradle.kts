plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    api(libs.kotlin.logging.jvm)

    testImplementation(project(":test-common"))
}
