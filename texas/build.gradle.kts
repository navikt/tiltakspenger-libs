plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    // httpklient er api: TexasHttpClient tar HttpTransport i konstruktøren og TexasSystemTokenProvider implementerer AuthTokenProvider.
    api(project(":httpklient:httpklient-infrastruktur"))

    implementation(libs.jackson.annotations)
    implementation(libs.ktor.server.auth)
    implementation(libs.arrow.core)
    implementation(project(":logging"))
    implementation(project(":common"))
    implementation(project(":json"))
    implementation(project(":ktor-common"))

    testImplementation(project(":test-common"))
    testImplementation(project(":ktor-test-common"))
    testImplementation(project(":auth-test-core"))
    testImplementation(testFixtures(project(":httpklient:httpklient-infrastruktur")))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
    // Kun for TexasAuthenticationProviderTest sin ktor-testserver; produksjonskoden bruker httpklient/libs-json.
    testImplementation(libs.ktor.serialization.jackson3)
    testImplementation(libs.arrow.core.jackson)
}

kover {
    currentProject {
        createVariant("httpklientKlienter") {
            add("jvm")
        }
    }

    reports {
        // Klientene på felles HttpKlient holdes på 100 % via en filtrert rapportvariant (per-regel-filter finnes ikke i kover-DSL-en).
        variant("httpklientKlienter") {
            filters {
                includes {
                    classes(
                        "no.nav.tiltakspenger.libs.texas.client.TexasHttpClient*",
                        "no.nav.tiltakspenger.libs.texas.client.TexasSystemTokenProvider*",
                    )
                }
            }
            verify {
                rule {
                    minBound(100)
                }
            }
        }
        verify {
            rule {
                // TODO kover: backfill til 100 % dekning — https://github.com/navikt/tiltakspenger-libs/issues/522
                // Ratchet, ikke mål: gulvet er målt dekning ved innføringen, slik at dekningen aldri kan regressere.
                // Målet er 100 som i httpklient/jobber; gapet er udekket kode fra før HttpKlient-migreringen.
                minBound(74)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.koverVerify, tasks.named("koverVerifyHttpklientKlienter"))
}
