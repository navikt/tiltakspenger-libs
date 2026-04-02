plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    api(project(":common"))
    api(project(":json"))
    api(project(":logging"))
    api(project(":test-common"))

    api(libs.arrow.core)

    // Auth
    api(libs.auth0.java.jwt)
    api(libs.auth0.jwks.rsa)
}
