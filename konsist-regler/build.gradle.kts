plugins {
    id("tiltakspenger.bibliotek")
}

dependencies {
    // `api` slik at konsumentene får Konsist-typene (KoScope) transitivt og versjonen styres ett sted.
    api(libs.konsist)

    // Bevisst ikke test-common: denne modulen skal stå på egne ben, og trenger kun assertions.
    testImplementation(libs.kotest.assertions.core)
}

// --- Arkitekturtestene kjøres alltid ------------------------------------------
// Gradles bygg-cache er uavhengig av filsti, så to utsjekker av samme commit får samme cache-nøkkel for `test`.
// Målt i en klone med et arbeidstre under seg: en grønn kjøring i hovedtreet ble servert i arbeidstreet som
// FROM-CACHE, uten at en eneste test kjørte.
//
// Konsist leser kildefilene fra disk mens testen kjører, og de er ikke deklarert som input til tasken.
// Reglene skanner hele repoet, også markdown, avro-skjemaer og de andre modulene, så cache-nøkkelen dekker
// ikke det testen faktisk leser.
//
// Arkitekturtestene skilles derfor ut på taggen `arkitektur` og kjøres i en egen task som verken er
// UP-TO-DATE eller leser bygg-cachen. Resten av testene i modulen er vanlige enhetstester og caches som før.
//
// Alternativet er å deklarere hele lesesettet som input til tasken. Det gir korrekt gjenbruk i stedet for
// ingen, men krever at lista holdes i synk med det reglene skanner. Bommer lista, er resultatet en grønn
// kjøring som aldri skjedde. Tasken tar sekunder, så det er lite å spare.
val arkitekturTest = tasks.register<Test>("arkitekturTest") {
    group = "verification"
    description = "Kjører arkitekturtestene mot hele repoet, uten gjenbruk fra bygg-cachen."
    val testkilder = sourceSets.named("test")
    testClassesDirs = testkilder.get().output.classesDirs
    classpath = testkilder.get().runtimeClasspath
    useJUnitPlatform { includeTags("arkitektur") }
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

tasks.named<Test>("test") {
    // Uten dette ville arkitekturtestene kjørt to ganger, og den ene gangen fra cache.
    useJUnitPlatform { excludeTags("arkitektur") }
    dependsOn(arkitekturTest)
}
