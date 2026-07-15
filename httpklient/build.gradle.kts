plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
    // FakeHttpTransport publiseres som testFixtures-variant slik at konsumentene tester mot den ekte pipelinen med byttet transport.
    `java-test-fixtures`
}

dependencies {
    api(libs.arrow.core)
    api(libs.arrow.resilience)
    api(project(":common"))
    // kotlin-logging er del av httpklient sitt public API: HttpKlientError.loggFeil tar imot KLogger.
    api(libs.kotlin.logging.jvm)

    // logging-modulen brukes kun internt (Sikkerlogg i loggFeil/loggTilSikkerlogg), så den skal ikke eksponeres på konsumentenes compile classpath.
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(libs.kotlinx.coroutines.core)
    // kotlin-reflect brukes direkte internt (kotlin.reflect.jvm.javaType i InternalResponseConversion); deklareres eksplisitt i stedet for å lene seg på en transitiv kopi fra json/Jackson.
    // kotlin("reflect") holder versjonen i synk med Kotlin-pluginet.
    implementation(kotlin("reflect"))

    // FakeHttpTransport serialiserer DTO-er med felles objectMapper i leggIKøJson.
    testFixturesImplementation(project(":json"))

    testImplementation(project(":test-common"))
    testImplementation(testFixtures(project(":httpklient")))
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
