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
- **Convention-plugins**: Delt build-logikk ligger i det inkluderte bygget `build-logic/`, ikke i `buildSrc`.
  `buildSrc` invaliderer hele bygget ved hver endring, og kan aldri publiseres videre til app-repoene slik et inkludert bygg kan.
  `build-logic` publiseres til GitHub Packages med plugin-markører, slik at app-repoene kan skrive `id("tiltakspenger.kotlin")`.
  Pluginene komponeres, slik at en modul bare tar i bruk det den faktisk er:
  - `tiltakspenger.kotlin` — grunnkonvensjonen: Kotlin/JVM-target og toolchain, compiler-flagg, Spotless med pinnet ktlint-versjon fra `gradle/libs.versions.toml`, JUnit 5-oppsett, ekskludering av JUnit 4 og gaten `verifiserHttpKlienter`.
  - `tiltakspenger.bibliotek` — grunnkonvensjonen pluss `java-library`, sources-jar og publisering til GitHub Packages.
    Alle bibliotekmodulene bruker denne; `versjonskatalog` og `plattform` publiserer andre artefakttyper og bruker `tiltakspenger.publisering` direkte.
  - `tiltakspenger.dekning` — Kover med krav om full linjedekning, koblet på `check`.
    Brukes av `arenatiltak-dtos`, `httpklient-domene`, `httpklient-infrastruktur`, `jobber`, `json`, `kafka-avro`, `ktor-common`, `ktor-test-common`, `meldekort-dtos`, `personklient-infrastruktur`, `texas` og begge `tiltaksdeltakelse`-modulene.
    Grendekning trappes opp per modul, se under.
  - `tiltakspenger.githooks` — installerer `.gitHooks/` i `.git/hooks/`.
    Ligger på rotprosjektet, siden hooks er per utsjekk.

  **Grendekning** legges på i tillegg til linjedekningen, aldri i stedet for: full linjedekning sier ingenting om hvilken vei et vilkår ble tatt, og full grendekning sier ingenting om en linje uten grener.
  Modulen trapper opp i sitt eget tempo med `dekning { grener = ... }`:
  - `Grendekning.AV` (standard) — ingen grenregel.
  - `Grendekning.RAPPORTER` — avviket logges som advarsel, bygget forblir grønt.
    Bruk dette til å se gapet før gaten smekker igjen.
  - `Grendekning.KREVES` — gate, bygget feiler under terskelen.

  `dekning { grenterskel = 90 }` senker terskelen fra 100 og gir en skralle: det modulen allerede har oppnådd, kan ikke falle tilbake mens gapet lukkes.
  Grenregelen ligger i rapportvarianten `grendekning` med egen task `koverVerifyGrendekning`, fordi Kovers `warningInsteadOfFailure` gjelder hele verify-blokka — lå den sammen med linjeregelen, ville `RAPPORTER` myket opp linjegaten også.

  Plugin- og bibliotekversjoner er sentralisert i `gradle/libs.versions.toml`, som `build-logic` leser fra samme fil.
- **Delt byggoppsett for app-repoene** publiseres fra to egne moduler, fordi de to lagene kan ulike ting:
  - `versjonskatalog` — publiserer `gradle/libs.versions.toml` som en versjonskatalog.
    Den deklarerer koordinater og versjoner, og ikke noe mer: en katalog kan verken uttrykke constraints eller `exclude`.
    Konsumeres i app-repoets `settings.gradle.kts` med `versionCatalogs { create("libs") { from("com.github.navikt.tiltakspenger-libs:versjonskatalog:<versjon>") } }`.
  - `plattform` — en `java-platform`-BOM med constraints som virker transitivt, altså det versjonskatalogen ikke kan.
    Den pinner libs-modulene til sin egen versjon, slik at app-repoene skriver libs-koordinatene uten versjon, og styrer de transitive versjonene vi ikke deklarerer selv (netty, jackson 2, kafka-clients med `strictly`, lz4, scram).
    Konsumeres med `implementation(platform("com.github.navikt.tiltakspenger-libs:plattform:<versjon>"))`.

  Modullista i plattformen utledes fra prosjektstrukturen, ikke fra en navneliste, så en ny libs-modul er med automatisk.
  `strictly` finnes ikke i Maven-POM-formatet og bæres av Gradle-metadataen ved siden av — det virker for Gradle-konsumenter, som er de eneste vi har.
  Repositories deklareres i `settings.gradle.kts` med `FAIL_ON_PROJECT_REPOS` — en modul som legger til sitt eget feiler bygget.
  Unntak fra HTTP-klientgaten deklareres i modulen selv, med begrunnelse: `httpKlientGuard { tillat("<koordinatprefiks>", "<begrunnelse>") }`.
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
| `lokal-oppstart` | Lokal utvikling: `startLokalPostgres(config, clock)` skaffer databasen en `LokalMain` trenger — starter compose-tjenesten fra monorepoets `docker-compose.yml` hvis den ikke allerede svarer, eller en Testcontainers-container med `LOKAL_DB_MODUS=testcontainers`. Alt som kan gå galt er modellert i `LokalPostgresFeil` med `hva`/`løsning`, og logges med `somMelding()`. Brukes fra test-sourcesettet (`testImplementation`), aldri fra produksjonskode |
| `httpklient`     | Felles HTTP-klient, delt i `httpklient-domene` (kun det domene-eksponerte: `HttpKlientResponse` og feil-/metadata-familien, alt i rotpakka) og `httpklient-infrastruktur` (alt under `httpklient.infra`: final `HttpKlient` med én typet metode per behov, config, pipelinen, `HttpTransport` i `infra.transport`, kall-typene i `infra.kall`, retry-/CB-motorene, `bodySomJson`, og `FakeHttpTransport` i testFixtures)                                  |
| `kafka`          | Kafka-oppsett i pakka `kafka.infra`: final `KafkaConfig` (ren data uten miljølesing, `fraNaisEnv()`-fabrikk, sealed `KafkaSikkerhet` Ingen/Ssl), `ManagedKafkaConsumer`, `Consumer`, `Producer`. Ingen egne lokal-/testklasser: lokalt konstrueres `KafkaConfig(kafkaBrokers = "localhost:9092")` direkte, og spesialtilfeller overstyres ved å plusse på resultatmappa. Ingen avro her — se `kafka-avro`                                                        |
| `kafka-avro`     | Avro-tillegg til `kafka` i pakka `kafka.avro.infra`: `AvroKafkaConfig` pakker inn en vilkårlig `KafkaConfig` (komposisjon, ikke arv) og legger på schema registry-oppsett med `SchemaRegistryBasicAuth?` som data. Med vilje ingen confluent-avhengighet (propertynavn hardkodet); kun avro-konsumenter skal avhenge av modulen                                                                                                                                  |
| `kafka-test`     | `SingletonKafkaProvider`: delt Testcontainers-broker for tester som trenger ekte Kafka                                                                                                                                                                                                                                                                                                                                                                          |
| `test-common`    | Delt test-infra: `fixedClock`, `TikkendeKlokke`, `getOrFail()` for `Either`, IPv4-trygge WireMock-hjelpere (`withWireMockServer`, `stoppedServerUri`, `ipv4WireMockServer`). HTTP-fake ligger ikke her — bruk `FakeHttpTransport` fra `testFixtures(project(":httpklient:httpklient-infrastruktur"))`                                                                                                                                                            |
| `texas`          | NAIS Texas auth: token-introspeksjon, system-tokens, Ktor auth provider, og `TexasSystemTokenProvider` (implementerer `httpklient` sin `AuthTokenProvider` med skip-cache-videreformidling)                                                                                                                                                                                                                                                                      |
| `ktor-common`    | Ktor-server-extensions (bruker `compileOnly` for ktor-deps). Pakken `...ktor.common.oppstart` har felles oppstartsmønster: `startKtorServer` (Netty + graceful shutdown + SIGTERM-under-oppstart-race), `konfigurerLivssyklus` (readiness via `ServerReady`/`ApplicationStopping`, trådsikker start/stopp), `StoppbarBakgrunnsprosess`/`stoppbarKafkaConsumer`, `startMedOpprydding`, `Readiness`-holder med `healthRoutes(erKlar)` + per-felt tekst-overstyring |
| `konsist-regler` | Delte Konsist-arkitekturregler for gjenbruk i alle repoene: `IngenJackson2`, `IngenJUnit4`, `IngenJupiterAsserts`, `IngenLokaleJacksonMappere` (forbyr mapper-konstruksjon utenfor libs/json, se navikt/tiltakspenger#30), `IngenNowUtenClock`, `IngenLocalDateTimeNow` (bruk `nå(clock)`), `IngenClockDefault` (Clock-parametre uten default-verdi), `IngenClockSystem` (systemklokke kun på composition root), `IngenRewriteAudienceTarget` (utgått Texas-flagg som har tatt ned prod to ganger), `EnSetningPerLinje`, `InfraImport`, `DomeneImportWhitelist`, `DomenepakkeUtenInfrastruktur` (en navngitt domenepakke har verken infra-underpakker eller infra-importer), `JsonbSkriving` (jsonb-parametre skrives som `:navn::jsonb` uten innpakning, og ingen `PGobject`), `BoundaryKlasser`, `IngenGlobalMocking` (mockkStatic/mockkObject muterer JVM-global tilstand), `IngenJUnitLivssyklus` (livssyklus settes globalt, ikke med @BeforeEach/@TestInstance), `IngenMuterbareTestfelter` (ingen delt muterbar tilstand i testklassers felter), `WireMockKunForWireFormat` (FakeHttpTransport som standard, WireMock kun i whitelistede wire-format-tester), `RouteBuilderKontrakt` (route-buildere tar `forventet: ForventetRespons?`, ingen ReturnerRespons-overloads eller egne assertions), `Testparallellitet` (junit-parallellkonfig låst og i sync mellom build.gradle.kts og junit-platform.properties) og `IsolertDatabasetestKonvensjon` (`runIsolated = true` krever markørannotasjon og begrunnelse) og `PersonopplysningMaskererToString` (en `data class` som markerer seg som `Personopplysning` må deklarere egen `toString()` — den genererte lekker verdien, og det er det ene hullet kompilatoren ikke ser) og `IngenInternalModifier` (`internal` betyr ingenting i et enmodul-repo som ikke publiseres — kjøres derfor i konsumentrepoene, ikke i libs). Hver regel har `brudd()` + `assert()`; konsumerende repo skriver én tynn Konsist-test per regel. Reglene som matcher på en liste eksponerer den som `standard…` og tar et `ekstra…`-argument som legges til — konsumenten kan skjerpe en delt regel, men ikke svekke den. Delte hjelpere i `Konsistregler.kt`: `assertSkanningenTraff` (vakt mot at en filtrert regel er grønn fordi den ikke fant noe å se på) og `assertWhitelistenErRyddet` (ratchet — en whitelistet fil som ikke lenger bryter, skal ut av whitelisten)                                                    |
| `jobber`         | Leader election + stoppable job-abstraksjoner for NAIS. `RunCheckFactory(leaderPodLookup, isReady = () -> Boolean)`; `RunJobCheck.shouldRun()` returnerer `Either<JobbSkalIkkeKjøre, Unit>` (domenefeil for innsikt i hvorfor en jobb hoppes over)                                                                                                                                                                                                               |
| `tiltaksdeltakelse` | Domenemodell for tiltaksdeltakelser, delt mellom saksbehandling-api og soknad-api. `tiltaksdeltakelse-domene` skiller kildens ord (`Kildestatus` med `Arenastatus`/`Kometstatus`/`TeamTiltakstatus`) fra vår tolkning (`Deltakerstatus`), og filtrerer ingenting bort på vei inn. Erstatter gradvis appen `tiltakspenger-tiltak` og `tiltak-dtos`, se [navikt/tiltakspenger#41](https://github.com/navikt/tiltakspenger/issues/41). Se modulens egen README |
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
Domenet inneholder kun det som eksponeres ut til konsumentenes domenelag: `HttpKlientResponse` (med `tryMap`/`loggSuksess`) og feil-/metadata-familien (`HttpKlientError`, `HttpKlientMetadata`, `HttpKlientTidsstempler` med hjelpere som `harStatus`/`loggFeil`), alt i rotpakka `no.nav.tiltakspenger.libs.httpklient`.
Alt annet er wiring og bor i infrastrukturen under `httpklient.infra`: klienten og config i `infra`-rot, `infra.transport`, `infra.kall` (`Statusregel`, `Header`/`NavHeadere`, `KlientAuth`/`AuthTokenProvider`, `SerialisertJson`, `HttpMethod`, `MultipartDel`/`MultipartDeler`), `infra.retry`, `infra.circuitbreaker`, og `bodySomJson` i `infra.feil` (json-modulen er aldri ok i domenet).
Det finnes ingen split packages på tvers av modulene.
Konsumenter avhenger av `project(":httpklient:httpklient-infrastruktur")`; domenet følger med transitivt som `api`.

Den offentlige klienten er den `final` klassen `HttpKlient(clock, config, transport)` — det finnes ikke noe interface, og det eneste som kan byttes ut er `HttpTransport` (transporten som rører nettverket).
API-et er én statisk typet metode per reelt behov (`getJson`, `getJsonEllerNull`, `postJson`, `postJsonEllerNull`, `postJsonUtenSvar`/`putJsonUtenSvar`/`patchJsonUtenSvar`, `postJsonMotPdf`, `getPdf`, `postBytesMotPdf`, `postMultipart`, `postTekst`, `postForm`); `Content-Type`, `Accept` og (de)serialisering er en intern konsekvens av metoden du kaller.
Binært innhold dekodes aldri som tekst i noen retning: både binære responser, `postBytesMotPdf`-bodyer og multipart-deler gjengis som placeholdere i `rawRequestString`/`rawResponseString`, slik at metadataen alltid er sikkerlogg-trygg.
`postBytesMotPdf` er den eneste metoden som tar `Content-Type` som parameter, fordi payloaden selv bestemmer den (`image/png` vs `image/jpeg`).
Multipart-deler sendes som samletypen `MultipartDeler` (`Nel<MultipartDel>` med guards for «minst én del» og unike feltnavn), ikke som en naken `List` — invariantene hører hjemme i typen, ikke på kallstedet.
All konfig er ren data i `HttpKlientConfig` (timeout, `KlientAuth`, `Retry`, `CircuitBreakerConfig`, skip-cache-statuser); det finnes ingen per-kall-overstyringer — et endepunkt med avvikende behov får en egen klientinstans.
Statuser som betyr suksess uttrykkes med `Statusregel` (data, ikke predikater); statuser som bærer et domeneutfall (f.eks. `403`/`409` med strukturert body) skal ikke inn i statusregelen, men utledes fra feiltypen med `harStatus` og `bodySomJson`.
Klienten logger aldri selv — konsumentene bruker `HttpKlientError.loggFeil` og `HttpKlientResponse.loggSuksess` fra laget som har domenekonteksten.
Ferdigserialisert JSON sendes med `SerialisertJson`-wrapperen (aldri en `String`-overload); egne request-headere settes med `Header`/`NavHeadere`, som avviser de reserverte navnene klienten selv eier.
De reified metodene er tynne inline-fasader som kun fanger typeargumentet og delegerer til `@PublishedApi internal`-broer, slik at de interne modellene (`HttpKlientRequest`, `ResponsFormat`) ikke lekker inn i public inline-bytecode.

Tester utenfor `httpklient`-modulen bruker `FakeHttpTransport` fra modulens testFixtures (`testImplementation(testFixtures(project(":httpklient:httpklient-infrastruktur")))`): en ekte `HttpKlient` med kø-basert transport, slik at hele den reelle pipelinen (auth, retry-gates, statusregler, Jackson, metadata, maskering) kjører i test i stedet for å emuleres.
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
./gradlew :<modul>:koverXmlReport            # coverage-rapport for én kover-modul, f.eks. :jobber eller :texas
```

- `spotlessApply` kjøres med `--no-parallel --max-workers=1` fra hjelpeskriptene, fordi Spotless + ktlint kan kaste en flaky `InvocationTargetException` når flere `spotlessKotlin`-tasks initialiserer ktlint parallelt.
  **Foretrekk hjelpeskriptene** fremfor `./gradlew clean spotlessApply build`.
- En slik `InvocationTargetException` kan fossilere seg i modulens `build/spotless/`-intermediater og replaye deterministisk på senere kjøringer.
  Kuren er `./gradlew :<modul>:clean` og ny kjøring.
- Spotless ekskluderer `**/resources/**`, fordi default-målet kommer fra `SourceSet.allSource` — som også inneholder resources.
  `.kt`-filene under `konsist-regler/src/test/resources/fixtures/` er input til reglene, ikke kildekode, og innholdet er poenget: uten ekskluderingen stripper `no-unused-imports` nettopp importene bruddfixturene skal bli tatt på.
  Formatér aldri fixturene — de skal stå ordrett.
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
- Delt build-konfig (Kotlin/JVM-versjon, spotless-konfig, compiler-flagg, JUnit 4-ekskludering, `per_class`-testlivssyklus) ligger i `build-logic/src/main/kotlin/tiltakspenger.kotlin.gradle.kts` — sjekk der før du endrer build-oppførsel i enkeltmoduler.

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
