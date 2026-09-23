import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import no.nav.tiltakspenger.byggelogikk.Dekning
import no.nav.tiltakspenger.byggelogikk.Grendekning

/**
 * Dekningsgaten: modulen skal ha full linjedekning, og `check` feiler hvis den faller.
 *
 * Grendekning legges på i tillegg, og trappes opp per modul med `dekning { grener = ... }`.
 * Den ligger i en egen rapportvariant fordi Kovers `warningInsteadOfFailure` gjelder hele verify-blokka:
 * lå grenregelen sammen med linjeregelen, ville RAPPORTER-trinnet myket opp linjegaten også.
 */

plugins {
    id("org.jetbrains.kotlinx.kover")
}

val dekning = extensions.create<Dekning>("dekning")
dekning.grener.convention(Grendekning.AV)
dekning.grenterskel.convention(100)

// Verdiene leses som Provider, ikke med .get(): pluginen kjører når den appliseres, altså før modulens egen `dekning { }`-blokk.
val grener = dekning.grener
val erAv = grener.map { it == Grendekning.AV }

kover {
    currentProject {
        createVariant("grendekning") {
            add("jvm")
        }
    }
    reports {
        total {
            verify {
                rule("full linjedekning") {
                    minBound(100)
                }
            }
        }
        variant("grendekning") {
            verify {
                warningInsteadOfFailure = grener.map { it == Grendekning.RAPPORTER }
                rule("full grendekning") {
                    disabled = erAv
                    bound {
                        minValue = dekning.grenterskel
                        coverageUnits = CoverageUnit.BRANCH
                    }
                }
            }
        }
    }
}

// koverVerify henger ikke på `check` av seg selv, og en dekningsgate ingen kjører er ingen gate.
tasks.named("check") {
    dependsOn(tasks.named("koverVerify"))
    // Grenrapporten kobles kun på når modulen har begynt på grendekning, så AV-moduler ikke betaler for en rapport ingen leser.
    dependsOn(erAv.map { av -> if (av) emptyList() else listOf("koverVerifyGrendekning") })
}
