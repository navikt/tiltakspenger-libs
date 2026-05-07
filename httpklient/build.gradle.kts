plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    api(libs.arrow.core)
    api(libs.arrow.resilience)
    api(project(":common"))
    // kotlin-logging er del av httpklient sitt public API: HttpKlientLoggingConfig.logger eksponerer KLogger.
    api(libs.kotlin.logging.jvm)

    // logging-modulen brukes kun internt (Sikkerlogg i InternalLogging), så den skal ikke eksponeres på konsumentenes compile classpath.
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(libs.kotlinx.coroutines.core)
    // kotlin-reflect brukes direkte internt (kotlin.reflect.jvm.javaType i InternalResponseConversion); deklareres eksplisitt i stedet for å lene seg på en transitiv kopi fra json/Jackson.
    // kotlin("reflect") holder versjonen i synk med Kotlin-pluginet.
    implementation(kotlin("reflect"))

    testImplementation(project(":test-common"))
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
