import no.nav.tiltakspenger.byggelogikk.Grendekning

plugins {
    id("tiltakspenger.bibliotek")
    id("tiltakspenger.dekning")
}

dependencies {
    // Domenet er api: modulen produserer og eksponerer domenetypene (Tiltaksdeltakelser, Kildestatus, ...).
    api(project(":tiltaksdeltakelse:tiltaksdeltakelse-domene"))

    implementation(project(":common"))
    implementation(project(":logging"))
    // json gir den delte objectMapper-en og Jackson-annotasjonene DTO-kopien bruker.
    implementation(project(":json"))
    implementation(project(":httpklient:httpklient-infrastruktur"))

    implementation(libs.arrow.core)

    testImplementation(project(":test-common"))
    testImplementation(testFixtures(project(":httpklient:httpklient-infrastruktur")))
    testImplementation(testFixtures(project(":tiltaksdeltakelse:tiltaksdeltakelse-domene")))
    // Paritetsvakt: kodetabellene verifiseres mot kjeden som avvikles så lenge tiltak-dtos finnes.
    testImplementation(project(":tiltak-dtos"))
}

dekning {
    grener = Grendekning.KREVES
}

tasks.withType<Jar> {
    archiveBaseName.set("tiltaksdeltakelse-infrastruktur")
}
