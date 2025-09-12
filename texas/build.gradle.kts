val jacksonVersion = "2.19.2"
val ktorVersion = "3.3.0"

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.arrow-kt:arrow-core:2.1.2")
    implementation(project(":logging"))
    implementation(project(":common"))
    implementation(project(":json"))
    implementation(project(":ktor-common"))
    implementation(project(":auth-core"))

    testImplementation(project(":test-common"))
    testImplementation(project(":ktor-test-common"))
    testImplementation(project(":auth-test-core"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.arrow-kt:arrow-core-jackson:2.1.2")
}
