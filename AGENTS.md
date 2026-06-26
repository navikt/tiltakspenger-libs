# AGENTS.md — tiltakspenger-libs

Dette repoet følger monorepo-konvensjonene i [`../AGENTS.md`](../AGENTS.md) og Kotlin/JVM-backendkonvensjonene i [`../AGENTS-backend.md`](../AGENTS-backend.md). Les disse først.


## Oversikt

Monorepo for delte Kotlin-biblioteker.
Publiseres til GitHub Packages.
**Deployes ikke til NAIS.**
Brukes av `tiltakspenger-saksbehandling-api`, `tiltakspenger-soknad-api`, `tiltakspenger-meldekort-api`, `tiltakspenger-datadeling`, `tiltakspenger-tiltak` med flere.

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
| `httpklient`    | Delt `java.net.http.HttpClient`-innpakning med `HttpKlient`, Arrow `Either`-feil, JSON-hjelpere, valgfri bearer-token-støtte via `AccessToken`, valgfri retry basert på Arrow Resilience `Schedule` (standard predikat for idempotente metoder, per-forsøk-timing i metadata, hook for overdreven retry), og valgfri circuit breaker basert på Arrow Resilience `CircuitBreaker` |
| `test-common`   | Delt test-infra: `fixedClock`, `TikkendeKlokke`, `getOrFail()` for `Either`, `HttpKlientFake`, wiremock-hjelpere                          |
| `texas`         | NAIS Texas auth: token-introspeksjon, system-tokens, Ktor auth provider                                                                   |
| `ktor-common`   | Ktor-server-extensions (bruker `compileOnly` for ktor-deps)                                                                               |
| `jobber`        | Leader election + stoppable job-abstraksjoner for NAIS                                                                                    |
| `*-dtos`        | API-kontraktstyper delt mellom tjenester                                                                                                  |

## Konvensjoner

### KDoc og kommentarer

Skriv **én setning per linje** i KDoc og kommentarer — ikke bryt én setning over flere linjer, og ikke slå flere setninger sammen på én linje.
Dette gir renere differ (en endret setning berører kun én linje) og er enklere å lese, søke i og vedlikeholde.
Maks linjelengde er bevisst slått av i ktlint-konfigurasjonen, så en lang setning skal stå på én linje selv om den blir bred.

### `httpklient`-struktur

Den offentlige klientkontrakten er `HttpKlient`.
Konsumenter oppretter en klient via `HttpKlient(clock = ...) { ... }`-fabrikken, som konfigurerer og returnerer den interne `JavaHttpKlient`-implementasjonen (basert på `java.net.http.HttpClient`).
Implementasjonstypen er bevisst `internal` slik at kallere kun avhenger av interfacet.
Alle standardverdier — connect/timeout, predikat for vellykkede statuser, logging, retry, circuit breaker, auth-token-leverandør — settes via `HttpKlient.HttpKlientConfig`-DSL-en som sendes til fabrikken; per-request-overstyringer på `RequestBuilder` har prioritet.
Tester utenfor `httpklient`-modulen skal generelt bruke `HttpKlientFake` fra `test-common`; tester inne i `httpklient` skal teste den virkelige implementasjonen mot WireMock/rå sockets der det er praktisk.

Retry-relaterte typer ligger i pakka `no.nav.tiltakspenger.libs.httpklient.retry` (kildefiler under `httpklient/src/main/kotlin/httpklient/retry/`) for å holde retry-logikk nær `RetryConfig` og adskilt fra `JavaHttpKlient` — pakkenavnet følger mappestrukturen.
Circuit breaker-relaterte typer ligger tilsvarende i `no.nav.tiltakspenger.libs.httpklient.circuitbreaker` (under `httpklient/src/main/kotlin/httpklient/circuitbreaker/`).
`CircuitBreakerConfig.None` er standard; aktiverte konfigurasjoner er opt-in, fluent, eksplisitt navngitte, støttet av Arrow Resilience `CircuitBreaker`, og tilstand er lokal per `HttpKlient`-instans per circuit breaker-navn.
Circuit breaker-beskyttelse omslutter hele retry-kjøringen, slik at kun det endelige resultatet etter retries registreres.

### Ingen standardverdier i domenetyper eller offentlige API-er

Standardverdier hører hjemme i **konfig-/builder-objekter** (f.eks. `HttpKlientLoggingConfig`, `RetryConfig`), **ikke** i databærere, domenemodeller eller konstruktørparametere som beskriver hva som faktisk skjedde eller hvem kalleren er.
Konkret:

- **Dataoppføringer som beskriver en hendelse/et resultat** (f.eks. `HttpKlientMetadata` — request/respons, antall forsøk, tidsbruk) må kreve alle felt eksplisitt. Standardverdier som `attempts = 1` eller `attemptDurations = emptyList()` skjuler feil der produsenten glemte å fylle ut feltet.
- **`Clock`-parametere** må være påkrevd i produksjonskode. Bruk aldri `Clock.systemUTC()` som standard i `main/`. Tester kan som regel bruke `fixedClock` eller `TikkendeKlokke` fra `test-common` som standard — nesten aldri `Clock.systemUTC()`.
- **Andre «ambient»-tjenester** (loggere, ID-generatorer, tilfeldighetskilder osv.) følger samme regel: påkrevd i produksjon, fornuftig teststandard i `test-common`.
- **Testhjelpere** som lager domene-verdier (f.eks. `tomMetadata()` i `httpklient`-testene) må fylle alle felt eksplisitt slik at testflaten er søkbar når typen endres.

Hvis du finner deg selv i å legge til en standardverdi for å få ødelagte/manglende kallsteder til å kompilere, **fiks kallstedene i stedet** — standardverdien skjuler problemet.

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

