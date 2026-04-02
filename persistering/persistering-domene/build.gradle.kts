plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":logging"))
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-common"))
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-domene")
}
