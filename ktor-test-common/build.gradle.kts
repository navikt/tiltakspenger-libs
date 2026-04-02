dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":auth-test-core"))

    implementation(libs.arrow.core)

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.server.core.jvm)
    compileOnly(libs.ktor.server.test.host)
    compileOnly(libs.ktor.server.test.host.jvm)

    testImplementation(project(":test-common"))

}
