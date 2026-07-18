# AGENTS.md — tiltakspenger-libs

Dette repoet følger monorepo-konvensjonene i [`../AGENTS.md`](../AGENTS.md) og Kotlin/JVM-backendkonvensjonene i [`../AGENTS-backend.md`](../AGENTS-backend.md).
Les disse først.


## Oversikt

Monorepo for delte Kotlin-biblioteker.
Publiseres til GitHub Packages.
**Deployes ikke til NAIS.**
Brukes av `tiltakspenger-saksbehandling-api`, `tiltakspenger-soknad-api`, `tiltakspenger-meldekort-api`, `tiltakspenger-datadeling`, `tiltakspenger-tiltak` med flere.

## Arkitektur

- **Gradle-submoduler** — se `settings.gradle.kts`.
  Hver submodul er et fokusert bibliotek (ID-er, DTO-er, klienter, hjelpere).
- **Convention-plugin**: Delt build-logikk ligger i `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts`.
  Alle submoduler tar den i bruk via `plugins { id("tiltakspenger-lib-conventions") }`.
  Den konfigurerer Kotlin/JVM-target, Spotless med pinnet ktlint-versjon fra `gradle/libs.versions.toml`, JUnit 5, publisering og ekskludering av JUnit 4.
  Plugin- og bibliotekversjoner er sentralisert i `gradle/libs.versions.toml`.
  Enkeltmoduler kan slå på JetBrains Kover for coverage (nå: `jobber` og `arenatiltak-dtos`).
- **Kildelayout**: standard Kotlin/Gradle-layout.
  Per [Kotlins kodekonvensjoner](https://kotlinlang.org/docs/coding-conventions.html#directory-structure) utelates den felles rotpakka `no.nav.tiltakspenger.libs` fra mappestrukturen (f.eks. `common/src/main/kotlin/common/SakId.kt` for pakka `no.nav.tiltakspenger.libs.common`).
- **Domene/infrastruktur-splitt**: Moduler med eksterne avhengigheter (HTTP-klienter, DB) deles i `*-domene` (rent domene, ingen eksterne deps) og `*-infrastruktur` (eksterne deps tillatt).
  Se `personklient/` og `persistering/`.
  Foreldre-/aggregator-prosjekter (`persistering/build.gradle.kts`, `personklient/build.gradle.kts`) disabler kun jar-taskene.
- **Kjerne-avhengighetskjede**: de fleste moduler avhenger av `common` → `logging`.
  Tester avhenger av `test-common`, som re-eksporterer `common`, kotest, mockk, wiremock og JUnit 5.

## Sentrale moduler

| Modul            | Formål                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common`         | Delte domenetyper: typede ID-er (`SakId`, `BehandlingId`, `MeldekortId`, `SøknadId`), `Bruker`/`Saksbehandler`, `CorrelationId`, ULID-base                                                                                                                                                                                                                                                                                                                       |
| `periodisering`  | Periodelogikk for datoer (`Periode`, `Periodisering`, `Tidslinje`)                                                                                                                                                                                                                                                                                                                                                                                               |
| `json`           | Delt Jackson-`objectMapper` + hjelperne `serialize()`/`deserialize()`                                                                                                                                                                                                                                                                                                                                                                                            |
| `logging`        | `Sikkerlogg` for NAIS secure logging via markers                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `httpklient`     | Felles HTTP-klient, delt i `httpklient-domene` (kun det domene-eksponerte: `HttpKlientResponse` og feil-/metadata-familien, alt i rotpakka) og `httpklient-infrastruktur` (alt under `httpklient.infra`: final `HttpKlient` med én typet metode per behov, config, pipelinen, `HttpTransport`-sømmen i `infra.transport`, kall-typene i `infra.kall`, retry-/CB-motorene, `bodySomJson`, og `FakeHttpTransport` i testFixtures)                                  |
| `test-common`    | Delt test-infra: `fixedClock`, `TikkendeKlokke`, `getOrFail()` for `Either`, IPv4-trygge WireMock-hjelpere (`withWireMockServer`, `stoppedServerUri`, `ipv4WireMockServer`). HTTP-fake ligger ikke her — bruk `FakeHttpTransport` fra `testFixtures(project(":httpklient:httpklient-infrastruktur"))`                                                                                                                                                            |
| `texas`          | NAIS Texas auth: token-introspeksjon, system-tokens, Ktor auth provider, og `TexasSystemTokenProvider` (implementerer `httpklient` sin `AuthTokenProvider` med skip-cache-videreformidling)                                                                                                                                                                                                                                                                      |
| `ktor-common`    | Ktor-server-extensions (bruker `compileOnly` for ktor-deps). Pakken `...ktor.common.oppstart` har felles oppstartsmønster: `startKtorServer` (Netty + graceful shutdown + SIGTERM-under-oppstart-race), `konfigurerLivssyklus` (readiness via `ServerReady`/`ApplicationStopping`, trådsikker start/stopp), `StoppbarBakgrunnsprosess`/`stoppbarKafkaConsumer`, `startMedOpprydding`, `Readiness`-holder med `healthRoutes(erKlar)` + per-felt tekst-overstyring |
| `konsist-regler` | Delte Konsist-arkitekturregler for gjenbruk i alle repoene: `IngenJackson2`, `IngenJUnit4`, `IngenJupiterAsserts`, `IngenLokaleJacksonMappere` (forbyr mapper-konstruksjon utenfor libs/json, se navikt/tiltakspenger#30), `IngenNowUtenClock`, `EnSetningPerLinje`, `InfraImport`, `DomeneImportWhitelist` og `BoundaryKlasser`. Hver regel har `brudd()` + `assert()`; konsumerende repo skriver én tynn Konsist-test per regel                                                    |
| `jobber`         | Leader election + stoppable job-abstraksjoner for NAIS. `RunCheckFactory(leaderPodLookup, isReady = () -> Boolean)`; `RunJobCheck.shouldRun()` returnerer `Either<JobbSkalIkkeKjøre, Unit>` (domenefeil for innsikt i hvorfor en jobb hoppes over)                                                                                                                                                                                                               |
| `*-dtos`         | API-kontraktstyper delt mellom tjenester                                                                                                                                                                                                                                                                                                                                                                                                                         |

## Konvensjoner

### KDoc og kommentarer

Skriv **én setning per linje** i KDoc og kommentarer — ikke bryt én setning over flere linjer, og ikke slå flere setninger sammen på én linje.
Dette gir renere differ (en endret setning berører kun én linje) og er enklere å lese, søke i og vedlikeholde.
Maks linjelengde er bevisst slått av i ktlint-konfigurasjonen, så en lang setning skal stå på én linje selv om den blir bred.

Aldri en import-linje kun for en KDoc-referanse — skriv heller lenken med full sti: `[no.nav.tiltakspenger.libs...Navn]`.
Refererer KDoc i en domene-modul noe som bor i en infrastruktur-modul, dropp klammene helt (backticks) — det finnes uansett ingen compiler-støtte på tvers av den avhengighetsretningen.

### `httpklient`-struktur

Modulen er delt i `httpklient:httpklient-domene` og `httpklient:httpklient-infrastruktur` etter samme mønster som `personklient`/`persistering`, med et aggregator-prosjekt uten artefakt.
Domenet inneholder kun det som eksponeres ut til konsumentenes domenelag: `HttpKlientResponse` (med `tryMap`/`loggTilSikkerlogg`) og feil-/metadata-familien (`HttpKlientError`, `HttpKlientMetadata`, `HttpKlientTidsstempler` med hjelpere som `harStatus`/`loggFeil`), alt i rotpakka `no.nav.tiltakspenger.libs.httpklient`.
Alt annet er wiring og bor i infrastrukturen under `httpklient.infra`: klienten og config i `infra`-rot, `infra.transport`, `infra.kall` (`Statusregel`, `Header`/`NavHeadere`, `KlientAuth`/`AuthTokenProvider`, `SerialisertJson`, `HttpMethod`), `infra.retry`, `infra.circuitbreaker`, og `bodySomJson` i `infra.feil` (json-modulen er aldri ok i domenet).
Det finnes ingen split packages på tvers av modulene.
Konsumenter avhenger av `project(":httpklient:httpklient-infrastruktur")`; domenet følger med transitivt som `api`.

Den offentlige klienten er den `final` klassen `HttpKlient(clock, config, transport)` — det finnes ikke noe interface, og den eneste sømmen er `HttpTransport` (transporten som rører nettverket).
API-et er én statisk typet metode per reelt behov (`getJson`, `getJsonEllerNull`, `postJson`, `postJsonEllerNull`, `postJsonUtenSvar`/`putJsonUtenSvar`/`patchJsonUtenSvar`, `postJsonMotPdf`, `getPdf`, `postTekst`, `postForm`); `Content-Type`, `Accept` og (de)serialisering er en intern konsekvens av metoden du kaller.
All konfig er ren data i `HttpKlientConfig` (timeout, `KlientAuth`, `Retry`, `CircuitBreakerConfig`, skip-cache-statuser); det finnes ingen per-kall-overstyringer — et endepunkt med avvikende behov får en egen klientinstans.
Statuser som betyr suksess uttrykkes med `Statusregel` (data, ikke predikater); statuser som bærer et domeneutfall (f.eks. `403`/`409` med strukturert body) skal ikke inn i statusregelen, men utledes fra feiltypen med `harStatus` og `bodySomJson`.
Klienten logger aldri selv — konsumentene bruker `HttpKlientError.loggFeil` og `HttpKlientResponse.loggTilSikkerlogg` fra laget som har domenekonteksten.
Ferdigserialisert JSON sendes med `SerialisertJson`-wrapperen (aldri en `String`-overload); egne request-headere settes med `Header`/`NavHeadere`, som avviser de reserverte navnene klienten selv eier.
De reified metodene er tynne inline-fasader som kun fanger typeargumentet og delegerer til `@PublishedApi internal`-broer, slik at de interne modellene (`HttpKlientRequest`, `ResponsFormat`) ikke lekker inn i public inline-bytecode.

Tester utenfor `httpklient`-modulen bruker `FakeHttpTransport` fra modulens testFixtures (`testImplementation(testFixtures(project(":httpklient:httpklient-infrastruktur")))`): en ekte `HttpKlient` med kø-basert transport, slik at hele den reelle pipelinen (auth, retry-gates, statusregler, Jackson, metadata, redaksjon) kjører i test i stedet for å emuleres.
Tester inne i `httpklient` tester transporten mot WireMock/rå sockets og pipelinen mot `FakeHttpTransport` (dogfooding).

Retry-relaterte typer (datamodell og motor) ligger samlet i `httpklient.infra.retry`; den offentlige `Retry`-datamodellen (`Ingen`/`Fast`/`Standard`) mapper til den interne Arrow `Schedule`-motoren, og idempotens-gaten (POST/PATCH retryes aldri uten eksplisitt `retryIkkeIdempotente = true`) kan ikke konfigureres bort.
Circuit breaker-relaterte typer (config og Arrow-fabrikk) ligger tilsvarende samlet i `httpklient.infra.circuitbreaker`.
`CircuitBreakerConfig.None` er standard; aktiverte konfigurasjoner er opt-in, fluent, eksplisitt navngitte, støttet av Arrow Resilience `CircuitBreaker`, og tilstand er lokal per `HttpKlient`-instans per circuit breaker-navn.
Circuit breaker-beskyttelse omslutter hele retry-kjøringen, slik at kun det endelige resultatet etter retries registreres.

### Ingen standardverdier i domenetyper eller offentlige API-er

Standardverdier hører hjemme i **konfig-/builder-objekter** (f.eks. `HttpKlientConfig`, `Retry`), **ikke** i databærere, domenemodeller eller konstruktørparametere som beskriver hva som faktisk skjedde eller hvem kalleren er.
Konkret:

- **Dataoppføringer som beskriver en hendelse/et resultat** (f.eks. `HttpKlientMetadata` — request/respons, antall forsøk, tidsbruk) må kreve alle felt eksplisitt.
  Standardverdier som `attempts = 1` eller `attemptDurations = emptyList()` skjuler feil der produsenten glemte å fylle ut feltet.
- **`Clock`-parametere** må være påkrevd i produksjonskode.
  Bruk aldri `Clock.systemUTC()` som standard i `main/`.
  Tester kan som regel bruke `fixedClock` eller `TikkendeKlokke` fra `test-common` som standard — nesten aldri `Clock.systemUTC()`.
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

- `spotlessApply` kjøres med `--no-parallel --max-workers=1` fra hjelpeskriptene, fordi Spotless + ktlint kan kaste en flaky `InvocationTargetException` når flere `spotlessKotlin`-tasks initialiserer ktlint parallelt.
  **Foretrekk hjelpeskriptene** fremfor `./gradlew clean spotlessApply build`.
- En slik `InvocationTargetException` kan fossilere seg i modulens `build/spotless/`-intermediater og replaye deterministisk på senere kjøringer.
  Kuren er `./gradlew :<modul>:clean` og ny kjøring.
- Configuration cache er aktivert.
  Unngå `System.getenv()` i build-skript — bruk `providers.environmentVariable()` i stedet.

### Stale coverage-data (kover) og cache-invalidering

Feiler `koverVerify` på kode endringen ikke har vært i nærheten av (typisk etter flytting av filer/moduler), er det nesten alltid stale coverage-data — ikke reell dekningsendring.
Fasit lokalt: `./gradlew clean check --no-build-cache` — merk at `clean check` alene ikke hjelper, siden build-cachen restorer de gamle resultatene.
Ikke senk `minBound`-gulv på grunnlag av et kover-tall som ikke er verifisert med kald kjøring.
Skjer det samme på GitHub-bygget, slett Gradle build-cachen for repoet (dependency-cachene kan stå):

```bash
for id in $(gh cache list --limit 100 --json id,key -q '.[] | select(.key | contains("build-cache")) | .id'); do gh cache delete "$id"; done
```
- Delt build-konfig (Kotlin/JVM-versjon, spotless-konfig, compiler-flagg, JUnit 4-ekskludering, `per_class`-testlivssyklus) ligger i `buildSrc/src/main/kotlin/tiltakspenger-lib-conventions.gradle.kts` — sjekk der før du endrer build-oppførsel i enkeltmoduler.

## CI og publisering

Publisering skjer fra `.github/workflows/push.yml`: tidsstempel-versjon (`0.0.<UTC-tidsstempel>`), GitHub Packages, SLSA-provenance-attestering av jar-ene og dependency graph-innsending.
Alle workflows nullstiller token-rettigheter på toppnivå (`permissions: {}`) og deklarerer eksplisitt per jobb — behold det mønsteret ved nye workflows/jobber.
Endrer du publisering eller CI-struktur, se README-seksjonen «Hvordan andre team i Nav gjør det» for sammenlignbare oppsett (dagpenger, tilleggsstønader, familie, etterlatte, aap).

## Avhengigheter

Minimér eksterne avhengigheter.
Bruk test-/compile-only-deps der det er mulig (`compileOnly` for ktor i `ktor-common`).
Se `gradle/libs.versions.toml` for version catalog og tillatte biblioteker.
Bruk de interne modulene `json` og `logging`, ikke tredjeparts-ekvivalenter.
Arrow er akseptert overalt.

