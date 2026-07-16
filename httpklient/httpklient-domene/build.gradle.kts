plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    // Either (tilDomene) og CircuitBreaker.ExecutionRejected (CircuitBreakerOpen) er del av modulens public API.
    api(libs.arrow.core)
    api(libs.arrow.resilience)
    // kotlin-logging er del av public API: HttpKlientError.loggFeil tar imot KLogger.
    api(libs.kotlin.logging.jvm)

    // Sikkerlogg brukes kun i loggFeil/loggTilSikkerlogg-kroppene og regnes foreløpig som domene-akseptabel; splittes i interface (domene) og impl (infra) på sikt.
    implementation(project(":logging"))

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

tasks.withType<Jar> {
    archiveBaseName.set("httpklient-domene")
}
