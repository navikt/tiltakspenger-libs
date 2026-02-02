val jacksonVersion = "3.0.4"
val jacksonAnnotationsVersion = "2.21"
dependencies {
    // Json
    api("tools.jackson.core:jackson-databind:$jacksonVersion")
    api("com.fasterxml.jackson.core:jackson-annotations:$jacksonAnnotationsVersion")
    api("tools.jackson.core:jackson-core:$jacksonVersion")
    api("tools.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("io.arrow-kt:arrow-core:2.2.1.1")
    implementation("io.arrow-kt:arrow-core-jackson:2.2.1.1")

    testImplementation(project(":test-common"))
}
