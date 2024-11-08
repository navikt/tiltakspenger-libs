val jacksonVersion = "2.18.1"
dependencies {
    // Json
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    api("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    api("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    testImplementation(project(":test-common"))
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
