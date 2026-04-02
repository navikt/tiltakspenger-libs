plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":person-dtos"))
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":common"))
    implementation(project(":personklient:personklient-domene"))

    implementation(libs.arrow.core)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.caffeine)

    testImplementation(project(":test-common"))
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-infrastruktur")
}
