dependencies {
    // Markør uten begrunnelse unntar ingenting — linja skal fortsatt flagges.
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // httpklient-unntak:

    constraints {
        // Constraint som pinner en CVE-fiks uten å legge klienten på noen classpath.
        api("org.apache.httpcomponents.core5:httpcore5:5.4.3") // httpklient-unntak: pinner CVE-fiks, aldri på classpath
    }
}
