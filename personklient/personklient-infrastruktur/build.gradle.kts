plugins {
    id("tiltakspenger-lib-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":person-dtos"))
    implementation(project(":logging"))
    implementation(project(":json"))
    implementation(project(":common"))
    implementation(project(":httpklient"))
    implementation(project(":personklient:personklient-domene"))

    implementation(libs.arrow.core)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.caffeine)

    testImplementation(project(":test-common"))
    testImplementation(testFixtures(project(":httpklient")))
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-infrastruktur")
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
                        "no.nav.tiltakspenger.libs.personklient.pdl.FellesHttpPersonklient*",
                        "no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklient",
                        "no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklient.Companion",
                        "no.nav.tiltakspenger.libs.personklient.pdl.HentPersonResponse*",
                        "no.nav.tiltakspenger.libs.personklient.skjerming.FellesHttpSkjermingsklient*",
                        "no.nav.tiltakspenger.libs.personklient.skjerming.FellesSkjermingsklient",
                        "no.nav.tiltakspenger.libs.personklient.skjerming.FellesSkjermingsklient.Companion",
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
                minBound(61)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.koverVerify, tasks.named("koverVerifyHttpklientKlienter"))
}
