plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    implementation(libs.r2dbc.postgresql)
    implementation(libs.kotliquery)
    implementation(project(":persistering:persistering-domene"))
    implementation(project(":persistering:persistering-infrastruktur"))

    testImplementation(project(":test-common"))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.r2dbc)
    testImplementation(libs.postgresql)
}

tasks.withType<Jar> {
    archiveBaseName.set("persistering-suspending")
}
