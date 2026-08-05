plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    api(libs.kotlin.logging.jvm)

    testImplementation(project(":test-common"))
}
