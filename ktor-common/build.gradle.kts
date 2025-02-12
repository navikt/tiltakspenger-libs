val ktorVersion = "3.1.0"
dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation("io.arrow-kt:arrow-core:1.2.4")

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly("io.ktor:ktor-server-core:$ktorVersion")
    compileOnly("io.ktor:ktor-server-core-jvm:$ktorVersion")

    testImplementation(project(":test-common"))

}
