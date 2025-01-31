dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation("io.arrow-kt:arrow-core:2.0.1")
    implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")

    testImplementation(project(":test-common"))
}
