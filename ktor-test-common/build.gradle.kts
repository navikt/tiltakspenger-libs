val ktorVersion = "3.3.1"
dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":auth-core"))
    implementation(project(":auth-test-core"))

    implementation("io.arrow-kt:arrow-core:2.1.2")

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly("io.ktor:ktor-server-core:$ktorVersion")
    compileOnly("io.ktor:ktor-server-core-jvm:$ktorVersion")
    compileOnly("io.ktor:ktor-server-test-host:$ktorVersion")
    compileOnly("io.ktor:ktor-server-test-host-jvm:$ktorVersion")

    testImplementation(project(":test-common"))

}
