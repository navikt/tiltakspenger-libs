dependencies {
    // Markør uten begrunnelse unntar ingenting — linja skal fortsatt flagges.
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // httpklient-unntak:
    // Markøren gjelder kun inne i constraints — en ekte avhengighet skal flagges selv med begrunnelse.
    testImplementation("com.squareup.retrofit2:retrofit:2.11.0") // httpklient-unntak: begrunnelsen hjelper ikke utenfor constraints

    constraints {
        // Constraint som pinner en CVE-fiks uten å legge klienten på noen classpath.
        api("org.apache.httpcomponents.core5:httpcore5:5.4.3") // httpklient-unntak: pinner CVE-fiks, aldri på classpath
        api("org.apache.kafka:kafka-clients") {
            version { strictly("4.3.1") }
        }
        // Den nøstede blokka over skal ikke ha lukket constraints for tidlig.
        api("org.apache.httpcomponents.client5:httpclient5:5.6.4") // httpklient-unntak: pinner CVE-fiks, aldri på classpath
        // Uten markør flagges en forbudt koordinat også inne i constraints.
        api("com.squareup.okhttp3:okhttp:4.12.0")
    }

    // Etter constraints-blokka gjelder markøren ikke lenger, heller ikke i et scope Gradle-gaten ikke ser.
    compileOnly("io.ktor:ktor-client-core:3.4.3") // httpklient-unntak: skal flagges likevel
    // Linja som åpner constraints regnes ikke som inne i blokka.
    implementation("com.squareup.retrofit2:retrofit:2.11.0"); constraints { } // httpklient-unntak: skal flagges likevel
}

// Klammer før `constraints` teller ikke, og linja som lukker blokka regnes ikke som inne i den.
dependencies { constraints {
    api("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3") // httpklient-unntak: pinner CVE-fiks, aldri på classpath
}; compileOnly("io.ktor:ktor-client-cio:3.4.3") // httpklient-unntak: skal flagges likevel
}
