val ktorVersion = "3.1.3"

dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation("io.arrow-kt:arrow-core:2.1.2")
    implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")

    implementation("io.ktor:ktor-utils:$ktorVersion")

    testImplementation(project(":test-common"))
}
