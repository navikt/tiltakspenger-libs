# tiltakspenger-libs

Felles Kotlin-bibliotek for tiltakspenger-tjenestene i Nav.
Publiseres til GitHub Packages og konsumeres av flere tjenester (bl.a. `tiltakspenger-saksbehandling-api`,
`tiltakspenger-soknad-api`, `tiltakspenger-meldekort-api`, `tiltakspenger-datadeling`, `tiltakspenger-tiltak`).

Se [AGENTS.md](AGENTS.md) for en grundig gjennomgang av arkitektur, moduler og konvensjoner.

## Bruk

Bibliotekene publiseres til GitHub Packages via CI. Konsumenter henter typisk via NAVs maven-mirror:

```kotlin
repositories {
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}
dependencies {
    implementation("com.github.navikt.tiltakspenger-libs:person-dtos:$felleslibVersion")
}
```

## Kom i gang (utvikling)

```bash
./lint_and_build.sh          # lint + build + test (foretrukket)
./clean_lint_and_build.sh    # clean + lint + build + test
./gradlew :<modul>:test      # kjør tester for én modul
```

## Struktur

- Gradle multi-modul prosjekt — se `settings.gradle.kts` for full liste.
- Felles byggekonfig i `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts`.
- Versjoner sentralisert i `gradle/libs.versions.toml`.
- Moduler med eksterne avhengigheter splittes i `*-domene` (ren domene) og `*-infrastruktur`.
- Standard layout: `src/main/kotlin/`, `src/test/kotlin/`. Felles rotpakke `no.nav.tiltakspenger.libs` utelates fra mappestrukturen.

## Kodekonvensjoner

- **Feilhåndtering:** Bruk Arrow `Either<Error, Success>` framfor exceptions. Ikke bruk `Option` — bruk nullable eller `Either`.
- **Typed IDs:** Følg eksisterende mønster i `common/` (privat konstruktør, `UlidBase`, `random()`/`fromString()`, `init`-blokker for invarianter).
- **Clock:** Bruk `java.time.Clock` som parameter. Aldri `now()` uten clock. Tester bruker `fixedClock`/`TikkendeKlokke` fra `test-common`.
- **JSON:** Bruk delt `objectMapper` fra `json`-modulen — ikke lag egne.
- **Logging:** Bruk `Sikkerlogg` fra `logging` for sensitive data, ellers `kotlin-logging`.
- **Imports:** Ingen star imports.
- **Stil:** Funksjonell stil, immutability, DDD — logikk på domeneobjektet nærmest dataene.
- **Tester:** Kotest assertions (`shouldBe`), ikke JUnit-assertions. JUnit 5 som runner.
- **Avhengigheter:** Hold minimalt. Bruk `compileOnly`/`testImplementation` der det passer.

## Språk- og navnekonvensjoner

Vi skriver norsk i kode og kommentarer. Tekniske termer brukes på engelsk når det er
bransjestandard (f.eks. `serialize`, `round-trip`, `nullable`, `top-level`).

- **Norsk i kode:** vi bruker æøå i filnavn, klassenavn, testnavn, variabelnavn, funksjonsnavn og kommentarer.
- **JSON-feltnavn (internt):** vi bruker æøå i feltnavn (f.eks. `"beløp"`, `"årsak"`).
  I `json`-modulen er Jackson Kotlin-modulen konfigurert med `KotlinPropertyNameAsImplicitName` slik at property-navnet bevares.
- **HTTP-endepunkter:** vi bruker *ikke* æøå i URL-stier — bruk ASCII (f.eks. `/soknad`, ikke `/søknad`). Dette gjelder paths, ikke request/response-bodyer.
- **Flyway-migreringer:** vi bruker *ikke* æøå i SQL-filnavn eller identifikatorer i migreringsskript (kolonnenavn, tabellnavn, indekser etc.). Bruk ASCII.
- **PostgresRepo-parametere:** vi bruker *ikke* æøå i navngitte parametere som sendes til databasen (`:soknadId`, ikke `:søknadId`). Verdier som lagres som JSONB kan inneholde æøå — det er bare parameternavn og kolonnenavn som må være ASCII.
