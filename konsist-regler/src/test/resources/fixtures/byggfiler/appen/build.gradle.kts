dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation(libs.okhttp)

    // implementation("com.squareup.retrofit2:retrofit:2.11.0") — kommentert ut, skal ikke flagges.
    testImplementation("org.wiremock:wiremock-jetty12:3.13.2")
    testImplementation(libs.kotlin.wiremock) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
}
