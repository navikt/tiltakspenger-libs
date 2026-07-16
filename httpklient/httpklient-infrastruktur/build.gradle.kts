plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
    // FakeHttpTransport publiseres som testFixtures-variant slik at konsumentene tester mot den ekte pipelinen med byttet transport.
    `java-test-fixtures`
}

dependencies {
    // Domenet (typer, feilmodell, config) er del av infrastruktur-modulens public API og følger med konsumentene transitivt.
    api(project(":httpklient:httpklient-domene"))
    // AccessToken er del av public API (bearerToken-parametrene og AuthTokenProvider.hentToken).
    api(project(":common"))

    // json-modulen er bevisst kun her (aldri i domene): serialisering av bodyer og bodySomJson-hjelperen.
    implementation(project(":json"))
    implementation(libs.kotlinx.coroutines.core)
    // kotlin-reflect brukes direkte internt (kotlin.reflect.jvm.javaType i InternalResponseConversion); deklareres eksplisitt i stedet for å lene seg på en transitiv kopi fra json/Jackson.
    // kotlin("reflect") holder versjonen i synk med Kotlin-pluginet.
    implementation(kotlin("reflect"))

    // FakeHttpTransport serialiserer DTO-er med felles objectMapper i leggIKøJson.
    testFixturesImplementation(project(":json"))

    testImplementation(project(":test-common"))
    testImplementation(testFixtures(project(":httpklient:httpklient-infrastruktur")))
}

kover {
    reports {
        verify {
            rule {
                minBound(100)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.koverVerify)
}

tasks.withType<Jar> {
    archiveBaseName.set("httpklient-infrastruktur")
}
