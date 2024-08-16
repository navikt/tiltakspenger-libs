val kotestVersion = "5.9.1"

dependencies {
    implementation(project(":common"))
    implementation(project(":persistering:persistering-domene"))

    implementation("io.kotest:kotest-assertions-core:$kotestVersion")
    implementation("io.kotest:kotest-assertions-json:$kotestVersion")
    implementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    implementation("io.kotest:kotest-extensions:$kotestVersion")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
