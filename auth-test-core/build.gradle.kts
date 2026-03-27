dependencies {
    api(project(":common"))
    api(project(":json"))
    api(project(":logging"))
    api(project(":test-common"))

    api("io.arrow-kt:arrow-core:2.2.2")

    // Auth
    api("com.auth0:java-jwt:4.5.1")
    api("com.auth0:jwks-rsa:0.23.0")
}
