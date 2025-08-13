val jacksonVersion = "2.19.2"
val ktorVersion = "3.2.3"

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation(project(":logging"))
    implementation(project(":common"))
    implementation(project(":json"))
}
