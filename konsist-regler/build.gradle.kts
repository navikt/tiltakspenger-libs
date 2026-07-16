plugins {
    id("tiltakspenger-lib-conventions")
}

dependencies {
    // `api` slik at konsumentene får Konsist-typene (KoScope) transitivt og versjonen styres ett sted.
    api(libs.konsist)

    // Bevisst ikke test-common: denne modulen skal stå på egne ben, og trenger kun assertions.
    testImplementation(libs.kotest.assertions.core)
}
