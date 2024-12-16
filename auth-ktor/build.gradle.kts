val ktorVersion = "3.0.2"
dependencies {
    implementation(project(":common"))
    api(project(":auth-core"))
    api(project(":ktor-common"))
    api(project(":logging"))

    implementation("io.arrow-kt:arrow-core:1.2.4")

    // Vi ønsker at konsumentene bruker sine egne versjoner av ktor
    compileOnly("io.ktor:ktor-server-core:$ktorVersion")
    compileOnly("io.ktor:ktor-server-core-jvm:$ktorVersion")

    testImplementation(project(":auth-test-core"))
    testImplementation(project(":test-common"))
    testImplementation(project(":ktor-test-common"))

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-call-id:$ktorVersion")
    testImplementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-cors:$ktorVersion")
    testImplementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-host-common:$ktorVersion")
    testImplementation("io.ktor:ktor-server-host-common-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    testImplementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    testImplementation("io.ktor:ktor-utils:$ktorVersion")


}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
