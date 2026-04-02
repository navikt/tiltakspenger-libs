plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    api(project(":persistering:persistering-domene"))

    implementation(libs.kotest.assertions.core)
    implementation(libs.kotlinx.coroutines.core)
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-test-common")
}

