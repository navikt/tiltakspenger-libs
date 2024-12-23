dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation("io.arrow-kt:arrow-core:2.0.0")
    implementation("org.jetbrains.kotlinx:atomicfu:0.26.1")

    testImplementation(project(":test-common"))
}
