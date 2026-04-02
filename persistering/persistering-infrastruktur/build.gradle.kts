plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":persistering:persistering-domene"))
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotliquery)
    implementation(libs.r2dbc.postgresql)

    testImplementation(project(":test-common"))

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.r2dbc)

    testImplementation(libs.postgresql)
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-infrastruktur")
}
