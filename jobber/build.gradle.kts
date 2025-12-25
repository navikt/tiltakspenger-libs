val ktorVersion = "3.3.3"
val kotlinxCoroutinesVersion = "1.10.2"
dependencies {
    implementation(project(":common"))
    implementation(project(":logging"))
    implementation(project(":json"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${kotlinxCoroutinesVersion}")
    implementation("io.arrow-kt:arrow-core:2.2.1.1")
    implementation("org.jetbrains.kotlinx:atomicfu:0.29.0")

    implementation("io.ktor:ktor-utils:$ktorVersion")

    testImplementation(project(":test-common"))
}
