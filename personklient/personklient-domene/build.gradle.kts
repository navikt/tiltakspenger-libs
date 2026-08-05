plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    // Feiltypene her bærer HttpKlientError videre til konsumentene (endepunkt, forsøk, varighet, rå request/respons), så den er del av public API.
    api(project(":httpklient:httpklient-domene"))
    implementation(project(":common"))
    implementation(project(":person-dtos"))
    implementation(libs.arrow.core)
}

tasks.withType<Jar> {
    archiveBaseName.set("personklient-domene")
}
