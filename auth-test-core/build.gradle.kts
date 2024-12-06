dependencies {
    api(project(":common"))
    api(project(":json"))
    api(project(":logging"))
    api(project(":test-common"))
    api(project(":auth-core"))

    api("io.arrow-kt:arrow-core:2.0.0")

    // Auth
    api("com.auth0:java-jwt:4.4.0")
    api("com.auth0:jwks-rsa:0.22.1")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
