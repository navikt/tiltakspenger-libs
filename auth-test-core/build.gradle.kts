dependencies {
    api(project(":common"))
    api(project(":json"))
    api(project(":logging"))
    api(project(":test-common"))
    api(project(":auth-core"))

    api("io.arrow-kt:arrow-core:2.1.2")

    // Auth
    api("com.auth0:java-jwt:4.5.0")
    api("com.auth0:jwks-rsa:0.22.2")
}
