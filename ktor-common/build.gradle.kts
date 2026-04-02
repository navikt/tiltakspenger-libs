dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation(libs.arrow.core)

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.server.core.jvm)

    testImplementation(project(":test-common"))
    testImplementation(libs.ktor.server.test.host)

}
