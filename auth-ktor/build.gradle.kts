val ktorVersion = "3.0.1"

val kotlinxCoroutinesVersion = "1.9.0"
dependencies {
    implementation(project(":common"))
    implementation(project(":auth-core"))
    implementation(project(":ktor-common"))

    implementation("io.arrow-kt:arrow-core:1.2.4")

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly("io.ktor:ktor-server-core:$ktorVersion")
    compileOnly("io.ktor:ktor-server-core-jvm:$ktorVersion")

    testImplementation(project(":test-common"))
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
