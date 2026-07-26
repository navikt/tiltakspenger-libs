dependencies {
    implementation("com.github.navikt.tiltakspenger-libs:httpklient:$felleslibVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")

    // Plugin til testApplication-klienten, ikke en nettverksklient — skal ikke flagges.
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
}

// Forbudslista til gate-tasken navngir klienter uten å deklarere dem — skal ikke flagges.
val forbudteHttpKlienter =
    listOf(
        "com.squareup.okhttp3",
        "com.konghq:unirest",
    )
