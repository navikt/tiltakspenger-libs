val kotestVersion = "5.9.1"

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.aallam.ulid:ulid-kotlin:1.3.0")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    testImplementation("io.kotest:kotest-extensions:$kotestVersion")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}
