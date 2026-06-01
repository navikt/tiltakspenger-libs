# AGENTS.md — tiltakspenger-libs

Dette repoet følger monorepo-konvensjonene i [`../AGENTS.md`](../AGENTS.md) og Kotlin/JVM-backendkonvensjonene i [`../AGENTS-backend.md`](../AGENTS-backend.md). Les disse først.


## Oversikt

Monorepo for delte Kotlin-biblioteker. Publiseres til GitHub Packages. **Deployes ikke til NAIS.** Brukes av `tiltakspenger-saksbehandling-api`, `tiltakspenger-soknad-api`, `tiltakspenger-meldekort-api`, `tiltakspenger-datadeling`, `tiltakspenger-tiltak` med flere.

## Arkitektur

- **Gradle-submoduler** — se `settings.gradle.kts`. Hver submodul er et fokusert bibliotek (ID-er, DTO-er, klienter, hjelpere).
- **Convention-plugin**: Delt build-logikk ligger i `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts`. Alle submoduler tar den i bruk via `plugins { id("tiltakspenger-lib-conventions") }`. Den konfigurerer Kotlin/JVM-target, Spotless med pinnet ktlint-versjon fra `gradle/libs.versions.toml`, JUnit 5, publisering og ekskludering av JUnit 4. Plugin- og bibliotekversjoner er sentralisert i `gradle/libs.versions.toml`. Enkeltmoduler kan slå på JetBrains Kover for coverage (nå: `jobber` og `arenatiltak-dtos`).
- **Kildelayout**: standard Kotlin/Gradle-layout. Per [Kotlins kodekonvensjoner](https://kotlinlang.org/docs/coding-conventions.html#directory-structure) utelates den felles rotpakka `no.nav.tiltakspenger.libs` fra mappestrukturen (f.eks. `common/src/main/kotlin/common/SakId.kt` for pakka `no.nav.tiltakspenger.libs.common`).
- **Domene/infrastruktur-splitt**: Moduler med eksterne avhengigheter (HTTP-klienter, DB) deles i `*-domene` (rent domene, ingen eksterne deps) og `*-infrastruktur` (eksterne deps tillatt). Se `personklient/` og `persistering/`. Foreldre-/aggregator-prosjekter (`persistering/build.gradle.kts`, `personklient/build.gradle.kts`) disabler kun jar-taskene.
- **Kjerne-avhengighetskjede**: de fleste moduler avhenger av `common` → `logging`. Tester avhenger av `test-common`, som re-eksporterer `common`, kotest, mockk, wiremock og JUnit 5.

## Sentrale moduler

| Modul           | Formål                                                                                                                                    |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `common`        | Delte domenetyper: typede ID-er (`SakId`, `BehandlingId`, `MeldekortId`, `SøknadId`), `Bruker`/`Saksbehandler`, `CorrelationId`, ULID-base |
| `periodisering` | Periodelogikk for datoer (`Periode`, `Periodisering`, `Tidslinje`)                                                                        |
| `json`          | Delt Jackson-`objectMapper` + hjelperne `serialize()`/`deserialize()`                                                                     |
| `logging`       | `Sikkerlogg` for NAIS secure logging via markers                                                                                          |
| `test-common`   | Delt test-infra: `fixedClock`, `TikkendeKlokke`, `getOrFail()` for `Either`, wiremock-hjelpere                                            |
| `texas`         | NAIS Texas auth: token-introspeksjon, system-tokens, Ktor auth provider                                                                   |
| `ktor-common`   | Ktor-server-extensions (bruker `compileOnly` for ktor-deps)                                                                               |
| `jobber`        | Leader election + stoppable job-abstraksjoner for NAIS                                                                                    |
| `*-dtos`        | API-kontraktstyper delt mellom tjenester                                                                                                  |

## Bygg og test (libs-spesifikt)

```bash
./lint_and_build.sh                          # lint + bygg + test (foretrukket)
./clean_lint_and_build.sh                    # clean + lint + bygg + test
./gradlew :<modul>:test                      # test én enkelt modul
./gradlew :jobber:koverXmlReport             # coverage-rapport for jobber
./gradlew :arenatiltak-dtos:koverXmlReport   # coverage-rapport for arenatiltak-dtos
```

- `spotlessApply` kjøres med `--no-parallel --max-workers=1` fra hjelpeskriptene, fordi Spotless + ktlint kan kaste en flaky `InvocationTargetException` når flere `spotlessKotlin`-tasks initialiserer ktlint parallelt. **Foretrekk hjelpeskriptene** fremfor `./gradlew clean spotlessApply build`.
- Configuration cache er aktivert. Unngå `System.getenv()` i build-skript — bruk `providers.environmentVariable()` i stedet.
- Delt build-konfig (Kotlin/JVM-versjon, spotless-konfig, compiler-flagg, JUnit 4-ekskludering, `per_class`-testlivssyklus) ligger i `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts` — sjekk der før du endrer build-oppførsel i enkeltmoduler.

## Avhengigheter

Minimér eksterne avhengigheter. Bruk test-/compile-only-deps der det er mulig (`compileOnly` for ktor i `ktor-common`). Se `gradle/libs.versions.toml` for version catalog og tillatte biblioteker.

