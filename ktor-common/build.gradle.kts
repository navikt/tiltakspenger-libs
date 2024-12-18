val ktorVersion = "3.0.2"
dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation("io.arrow-kt:arrow-core:2.0.0")

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly("io.ktor:ktor-server-core:$ktorVersion")
    compileOnly("io.ktor:ktor-server-core-jvm:$ktorVersion")

    testImplementation(project(":test-common"))

}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
