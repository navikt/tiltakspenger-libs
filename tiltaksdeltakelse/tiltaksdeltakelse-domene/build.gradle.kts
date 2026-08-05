import no.nav.tiltakspenger.byggelogikk.Grendekning

plugins {
    id("tiltakspenger.bibliotek")
    id("tiltakspenger.dekning")
    // Byggerne publiseres som testFixtures-variant slik at konsumentene og skyggekjøringen bygger deltakelser gjennom fabrikken, ikke egne kopier.
    `java-test-fixtures`
}

dependencies {
    // common er api: Virksomhetsnavn og Tilknytningstittel er del av den offentlige flaten.
    api(project(":common"))

    // periodisering er api: Periode er del av GirRett.MedPeriode sin offentlige flate.
    api(project(":periodisering"))

    testImplementation(project(":test-common"))
    testImplementation(testFixtures(project(":tiltaksdeltakelse:tiltaksdeltakelse-domene")))
}

dekning {
    grener = Grendekning.KREVES
}

tasks.withType<Jar> {
    archiveBaseName.set("tiltaksdeltakelse-domene")
}
