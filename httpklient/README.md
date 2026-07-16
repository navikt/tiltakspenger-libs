# httpklient

`httpklient` er en liten felles HTTP-klient basert på Java sin innebygde `java.net.http.HttpClient`.

Målet er å standardisere enkel HTTP-bruk i tiltakspenger-libs med:

- Arrow `Either<HttpKlientError, HttpKlientResponse<T>>` i stedet for exceptions i API-et (suksess-grenen er en `HttpKlientResponse<T>` med status, body og metadata).
- Egne venstresider for blant annet `Timeout`, `NetworkError`, `InvalidRequest`, `UventetStatus`, `SerializationError`, `DeserializationError` og `CircuitBreakerOpen`, gruppert i `RequestIkkeSendt`, `IngenRespons` og `ResponsMottatt` etter «så serveren requesten min?».
- Støtte for JSON som `String` inn/ut.
- Støtte for DTO-er inn/ut via `tiltakspenger-libs/json` sine `serialize`/`deserialize`-hjelpere.
- Suspend-first API med en Ktor-inspirert `RequestBuilder`.
- Verb-hjelpere for `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD` og `OPTIONS`.
- Felles `HttpKlientMetadata` på både `HttpKlientResponse` og `HttpKlientError` med rå request/response, headere og status der de finnes.
- Konfigurerbar definisjon av hvilke HTTP-statuser som regnes som suksess.
- Valgfri resilience via retry og circuit breaker basert på Arrow Resilience.
- Valgfri logging via vanlig `KLogger` og/eller `Sikkerlogg`.

## Eksempel

Bruk `HttpKlient` som avhengighetstype i konsumenter.
Produksjonsklienten opprettes via `HttpKlient(clock = ...)`-factoryen (den konkrete implementasjonen er intern).
Tester kan bruke `HttpKlientFake` fra `test-common`.

Generisk request med builder:

```kotlin
val klient: HttpKlient = HttpKlient(clock = clock)

val response: Either<HttpKlientError, HttpKlientResponse<MinResponseDto>> = klient.request<MinResponseDto>(
    uri = URI.create("https://example.com/api"),
    method = HttpMethod.POST,
) {
    header("X-Trace-Id", traceId)
    timeout = 5.seconds
    json(MinRequestDto(id = "123"))
}
```

Verb-hjelpere setter HTTP-metoden for deg:

```kotlin
val response = klient.get<MinResponseDto>(URI.create("https://example.com/api/123")) {
    header("X-Trace-Id", traceId)
    timeout = 5.seconds
}
```

For JSON som allerede er en `String`, bruk `String`-overloaden (strengen sendes verbatim, ikke serialisert på nytt):

```kotlin
val response = klient.post<String>(URI.create("https://example.com/api"), """{"id":"123"}""")
```

Tilsvarende finnes `post`/`put`/`patch` med en DTO som `body: Any` (serialiseres via `tiltakspenger-libs/json`).

For raw tekst uten JSON-headere:

```kotlin
val response = klient.post<String>(URI.create("https://example.com/api")) {
    header("Content-Type", "text/plain")
    body("hei")
}
```

For `application/x-www-form-urlencoded` (f.eks. token-endepunkter):

```kotlin
val response = klient.post<String>(URI.create("https://example.com/token")) {
    formUrlEncoded("grant_type" to "client_credentials", "scope" to "api://app-x")
}
```

### Response-typer og tomme bodyer

Response-typen `T` i `request<T>` / verb-hjelperne styrer hvordan bodyen tolkes:

- `String` — rå body uten deserialisering.
- `Unit` — bodyen ignoreres.
  Bruk denne for `204 No Content`, `HEAD` og andre svar uten body.
- En DTO — bodyen deserialiseres med `tiltakspenger-libs/json`.

Merk at en tom body _ikke_ kan deserialiseres til en DTO.
Et `204`-svar, et `HEAD`-svar eller et tomt `304`-svar med en DTO som response-type gir derfor `HttpKlientError.DeserializationError`.
Bruk `Unit` (eller `String`) som response-type for endepunkter som kan svare uten body.

## Testing

`test-common` eksponerer `HttpKlientFake`, en enkel fake som implementerer `HttpKlient`, tar opp alle requests som `HttpKlientRequest`, og svarer med køede responser/feil:

```kotlin
val httpKlient = HttpKlientFake().apply {
    enqueueResponse(MinResponseDto(id = "123"))
}

val response = httpKlient.get<MinResponseDto>(URI.create("https://example.com/api/123")).getOrFail()

httpKlient.requests.single().method shouldBe HttpMethod.GET
```

Hvis faken brukes uten konfigurert respons returneres en tydelig `HttpKlientError.InvalidRequest`
med `metadata.attempts = 0`, slik at tester feiler deterministisk uten å finne opp en defaultverdi.

## Metadata, headere og logging

`HttpKlientResponse` har alltid `responseHeaders` fra HTTP-responsen og `requestHeaders` slik requesten faktisk ble sendt etter at klienten har lagt til eventuelle standard JSON-headere.

Feiltypene i `HttpKlientError` har også `requestHeaders`, og `responseHeaders` når feilen kommer etter at en HTTP-respons er mottatt, for eksempel `UventetStatus` og `DeserializationError` (begge i gruppen `ResponsMottatt`, som garanterer non-null `statusCode` og rå-`body`).

Både vellykkede svar og feil har i tillegg `metadata`:

```kotlin
val metadata: HttpKlientMetadata = response.metadata
```

`HttpKlientMetadata` inneholder:

- `rawRequestString` — klientens tekstrepresentasjon av requesten (ikke garantert byte-for-byte wire-format fra Java `HttpClient`).
- `rawResponseString` — rå response-body når en response finnes.
- `requestHeaders` — effektive request-headere, med _uredigerte_ verdier (en bearer-token ligger her i klartekst).
  Logg derfor `rawRequestString` framfor `requestHeaders` direkte.
- `responseHeaders` — response-headere når en response finnes.
- `statusCode` — HTTP-status når en response finnes.
- `attempts` — antall forsøk som ble utført, inkludert det første. `1` betyr at det ikke ble retry-et, og `0` at det aldri ble gjort et HTTP-forsøk (pre-flight-feil eller åpen circuit breaker).
- `attemptDurations` — varighet per forsøk i den rekkefølgen de ble kjørt.
  Måles monotont via `timeSource` (default `TimeSource.Monotonic`), ikke mot veggklokka, så de er immune mot klokkejustering (NTP-hopp).
- `totalDuration` — total tid for hele kallet (inkludert backoff mellom forsøk), også målt monotont via `timeSource`.
- `tidsstempler` — absolutte veggklokke-`LocalDateTime`-er (samme semantikk som `nå(clock)`) for nøkkelpunktene i kallet, se under.

### Tidsstempler

`metadata.tidsstempler` (`HttpKlientTidsstempler`) utfyller de relative varighetene med faktiske `LocalDateTime`-er — samme semantikk som `nå(clock)` (klokkas sone, truncated til mikrosekunder for PostgreSQL-kompatibilitet) — slik at konsumenter som må lagre «når skjedde dette» (f.eks. et oversendt-tidspunkt mot et fagsystem) kan lese det rett fra klienten og lagre det direkte i et `LocalDateTime`-felt, i stedet for å kalle sin egen klokke ved siden av:

- `authStartet` / `authFullført` — rett før/etter `AuthTokenProvider.hentToken`. `null` når ingen provider faktisk ble kalt (per-request `bearerToken`, eksplisitt `Authorization`-header, eller ingen provider konfigurert).
- `requestSendt` — start på det _første_ HTTP-forsøket. `null` når det aldri ble gjort et reelt forsøk (pre-flight-feil eller åpen circuit breaker).
- `responsMottatt` — slutt på det _siste_ HTTP-forsøket, men bare når det forsøket faktisk ga en respons. `null` når det _endelige_ utfallet ikke ga en respons (timeout/nettverksfeil på siste forsøk), eller når det aldri ble gjort et forsøk.
  Metadata reflekterer alltid det endelige utfallet: fikk et _tidligere_ forsøk en respons, men siste forsøk timet ut, er `responsMottatt` likevel `null` (på linje med at `statusCode` og `rawResponseString` også er `null` for et slikt utfall).

Tidsstemplene er også tilgjengelige via convenience-aksessoren `error.tidsstempler` på `HttpKlientError` (på lik linje med `error.attempts` osv.).

```kotlin
val oversendtTidspunkt: LocalDateTime? = response.metadata.tidsstempler.requestSendt
```


Left-verdier fyller inn så mye metadata som finnes for feilsituasjonen.
For eksempel har `SerializationError` request-informasjon, men ingen response, mens `UventetStatus` har både request, response-body, response-headere og status.

Sensitive headere (`Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`) maskeres til `***` i `rawRequestString`, slik at bearer-tokens ikke lekker.
Selve HTTP-requesten sendes med de ekte verdiene, og det strukturerte `requestHeaders`-mappet beholder også de uredigerte verdiene (bruk `rawRequestString` hvis du skal logge).
For hva som maskeres i selve loggingen, se [Vanlig logg vs. Sikkerlogg (PII)](#vanlig-logg-vs-sikkerlogg-pii).

HTTP-headere er case-insensitive, så bruk hjelperne `responseHeader(name)` / `responseHeaderValues(name)` (og `requestHeader` / `requestHeaderValues`) på `HttpKlientMetadata` i stedet for å slå opp direkte i mappet:

```kotlin
val location: String? = response.metadata.responseHeader("Location")
val cookies: List<String> = response.metadata.responseHeaderValues("Set-Cookie")
```

### Feilgruppering

`HttpKlientError`-variantene er delt i tre under-grensesnitt etter spørsmålet «så serveren requesten min?».
Det er denne aksen som faktisk styrer hvor trygt det er å retry-e og om kallet kan ha hatt sideeffekter:

| Gruppe | Betydning | Varianter |
|---|---|---|
| `RequestIkkeSendt` | Ingenting ble sendt over nettverket. Serveren har garantert ikke sett requesten (`attempts = 0`). Trygt å bygge ny request og forsøke på nytt. | `InvalidRequest`, `SerializationError`, `AuthError`, `CircuitBreakerOpen` |
| `IngenRespons` | Et HTTP-forsøk ble startet, men ingen fullstendig respons kom tilbake. Ukjent om serveren rakk å behandle requesten. | `Timeout`, `NetworkError` |
| `ResponsMottatt` | Serveren svarte, så både `statusCode` og rå-`body` er garantert (i tillegg til `responseHeaders`). | `UventetStatus`, `DeserializationError` |

Du kan matche enten på den konkrete varianten eller på gruppen. `UventetStatus` het tidligere `Ikke2xx`; navnet er endret fordi feilen egentlig betyr «status ble ikke godtatt av det konfigurerte `successStatus`-predikatet», ikke bokstavelig «utenfor 2xx».

Logging er av som standard.
Den kan skrus på globalt:

```kotlin
val klient = HttpKlient(clock = clock) {
    logging = HttpKlientLoggingConfig(
        logger = logg,
        loggTilSikkerlogg = true,
        inkluderHeadere = false,
    )
}
```

Eller per request:

```kotlin
val response = klient.get<MinResponseDto>(URI.create("https://example.com/api/123")) {
    logging {
        logger = logg
        loggTilSikkerlogg = true
        inkluderHeadere = true
    }
}
```

### Granulær nivåstyring

Loggnivået styres per kategori av kall, slik at du kan skru ned støy uten å miste feillogging (og motsatt):

| Felt | Kategori | Default |
|---|---|---|
| `suksessNivå` | Kall godtatt av `successStatus`-predikatet | `INFO` |
| `klientfeilNivå` | Respons med `4xx` som ikke ble godtatt som suksess | `ERROR` |
| `serverfeilNivå` | Respons med annen uventet status (typisk `5xx`) | `ERROR` |
| `feilNivå` | Feil uten godtatt respons (transport, timeout, serialisering, deserialisering, auth, circuit breaker) | `ERROR` |
| `skipCacheRetryNivå` | Diagnostikk når en skip-cache-retry ikke hjalp: et ferskt token ble også avvist (typisk persistent `401`/`403`) | `WARN` |

Hver kategori settes til et `HttpKlientLogNivå` (`OFF`, `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`).
`OFF` slår kategorien helt av.
Nivået gjelder både `logger` og — når `loggTilSikkerlogg = true` — `Sikkerlogg` (`Sikkerlogg` har ikke `trace`, så `TRACE` mappes til `debug` der).

### Vanlig logg vs. Sikkerlogg (PII)

Vanlig `logger` skal aldri inneholde PII, mens `Sikkerlogg` kan.
Modulen håndhever dette i alle loggkategorier:

- **URI**: Til vanlig `logger` logges kun `scheme://host:port/path` — query-parametre, fragment og eventuell user-info (potensiell PII, f.eks. `?fnr=…`) strippes bort. `Sikkerlogg` får hele URI-en.
- **Headere** (`inkluderHeadere = true`): Til vanlig `logger` vises headernavn, men *alle* verdier maskeres til `***` (en egendefinert header kan inneholde PII). `Sikkerlogg` får de ekte verdiene, med unntak av de faste sensitive headerne (`Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`) som er hemmeligheter og alltid maskeres.
- **Respons-body**: Logges kun til `Sikkerlogg`, aldri til vanlig `logger`.

Vil du ha alt (full URI, ekte header-verdier, body) til ett sted, sett `loggTilSikkerlogg = true`.

```kotlin
val klient = HttpKlient(clock = clock) {
    logging = HttpKlientLoggingConfig(
        logger = logg,
        suksessNivå = HttpKlientLogNivå.OFF, // ingen støy fra vellykkede kall
        klientfeilNivå = HttpKlientLogNivå.INFO, // 4xx er forventet her, logg lavt
        // serverfeilNivå og feilNivå beholder ERROR
    )
}
```

## Suksess-statuser

Som standard er `2xx` suksess (`HttpStatusSuccess.is2xx`).
Dette kan overstyres globalt, enten med et eget predikat eller med en av hjelperne i `HttpStatusSuccess`:

```kotlin
val klient = HttpKlient(clock = clock) {
    successStatus = HttpStatusSuccess.exactly(200, 201, 204)
}
```

`HttpStatusSuccess` tilbyr `is2xx`, `exactly(vararg koder)` og `inRange(range)` — så du slipper å skrive predikatene selv.

Per request kan du sende inn et predikat, en liste av koder, eller en range:

```kotlin
// Eksplisitt predikat (f.eks. 2xx pluss 304)
klient.get<String>(URI.create("https://example.com/api/cache")) {
    successStatus { code -> HttpStatusSuccess.is2xx(code) || code == 304 }
}

// Bare noen eksakte koder
klient.get<String>(URI.create("https://example.com/api/cache")) {
    successStatus(200, 304)
}

// En range
klient.get<String>(URI.create("https://example.com/api/cache")) {
    successStatus(200..204)
}
```

## Retry

`HttpKlient` har valgfri retry-støtte basert på Arrow Resilience `Schedule`.
Default er `RetryConfig.None`, dvs. ingen retries.

Eksponentiell backoff og enkelt predikat via fabrikkmetode:

```kotlin
val klient = HttpKlient(clock = clock) {
    defaultRetry = RetryConfig.exponential(
        maxRetries = 3,
        initialDelay = 200.milliseconds,
        maxDelay = 2.seconds,
        jitter = true,
        retryOn = RetryOnServerErrorsAndNetwork,
    ).notifyOnExcessiveRetries(threshold = 2)
}
```

`RetryOnServerErrorsAndNetwork` tillater retry kun for idempotente metoder (`GET`, `HEAD`, `OPTIONS`, `PUT`, `DELETE`).
Hvilke utfall som i det hele tatt er retry-bare (`408`/`425`/`429`/`500`/`502`/`503`/`504` / `Timeout` / `NetworkError`) styres av `AttemptOutcome.retryable` som en hard gate før predikatet.
En respons som allerede regnes som suksess av det konfigurerte `successStatus`-predikatet retry-es aldri, selv om statuskoden er i den retryable mengden (f.eks. hvis en konsument bevisst godtar `503` som suksess).

For konstant backoff finnes `RetryConfig.fixed(maxRetries, delay)`.
Konsumenter kan også sende inn en Arrow `Schedule<AttemptOutcome, *>` direkte.
På den bare `RetryConfig(schedule = ...)`-konstruktøren defaulter `retryOn` til `NeverRetry`, så den retry-er ikke før du eksplisitt opt-iner (f.eks. `retryOn = RetryOnServerErrorsAndNetwork` eller `withRetryOn(...)`).
Fabrikkene `exponential`/`fixed` defaulter derimot til `RetryOnServerErrorsAndNetwork`, siden det å velge en av dem allerede uttrykker eksplisitt retry-intensjon:

```kotlin
val retryConfig = RetryConfig(
    schedule = Schedule.spaced<AttemptOutcome>(200.milliseconds)
        .zipLeft(Schedule.recurs(3)),
    retryOn = RetryOnServerErrorsAndNetwork,
)
```

Per-request override fungerer som for `successStatus` og `loggingConfig`:

```kotlin
val response = klient.post<String>(URI.create("https://example.com/api/idempotent-key")) {
    json(payload)
    retryConfig = RetryConfig.fixed(maxRetries = 2, delay = 100.milliseconds)
}
```

`HttpKlientMetadata` får alltid med `attempts`, `attemptDurations` og `totalDuration`, både på vellykkede svar og på alle `HttpKlientError`-varianter.
Disse kan brukes til å få et bilde av hvor mye tid og hvor mange retries et kall faktisk forbrukte.

`onExcessiveRetries` kalles når antall retries (`attempts - 1`) er minst `excessiveRetriesThreshold`.
Uten en egen hook logges et default-varsel via klientens `HttpKlientLoggingConfig` på `excessiveRetriesNivå` (default `WARN`) — skrur du av logging (eller setter kategorien til `OFF`), er også dette varselet stille.
Setter du en egen hook med `notifyOnExcessiveRetries(threshold) { ... }`, får den hele `RetryOutcome` (samme data som default-loggingen) og tar over ansvaret, f.eks. for å eksponere som metrikk.
Bruk `withoutExcessiveRetriesNotification()` for å skru av varslingen helt.

### Retryable-flagg

Hver `HttpKlientError` (og `AttemptOutcome` internt) eksponerer `retryable: Boolean`.
Retry-loopen bruker dette som en **hard gate** — den vil aldri forsøke på nytt for utfall som regnes som permanente, uansett hva [RetryConfig.retryOn] returnerer:

| Variant | `retryable` |
|---|---|
| `Timeout`, `NetworkError` | `true` |
| `UventetStatus` med status `408`, `425`, `429`, `500`, `502`, `503`, `504` | `true` |
| `UventetStatus` med øvrige statuser | `false` |
| `InvalidRequest`, `SerializationError`, `DeserializationError`, `AuthError`, `CircuitBreakerOpen` | `false` |

Dvs. selv et altfor liberalt `retryOn = { true }`-predikat vil aldri retry-e en validerings- eller deserialiseringsfeil eller en `404`.

## Circuit breaker

`HttpKlient` har valgfri circuit breaker-støtte basert på Arrow Resilience `CircuitBreaker`.
Default er `CircuitBreakerConfig.None`, dvs. ingen circuit breaker.

Konfigurasjon gjøres fluent på samme måte som retry:

```kotlin
val klient = HttpKlient(clock = clock) {
    defaultCircuitBreaker = CircuitBreakerConfig.count(
        name = "min-nedstroem",
        maxFailures = 5,
        resetTimeout = 30.seconds,
    ).withExponentialBackoff(
        factor = 2.0,
        maxResetTimeout = 5.minutes,
    ).doOnOpen {
        meterRegistry.counter("nedstrom_circuit_breaker_open").increment()
    }
}
```

`CircuitBreakerConfig.count(name, maxFailures, resetTimeout)` åpner etter `maxFailures` feil som matcher `failurePredicate`.
`name` er den stabile nøkkelen for breaker-state innenfor én `HttpKlient`-instans.
Bruk derfor samme navn for requests som skal dele breaker, også hvis configen bygges inline per request.
`name` må være lav-kardinalitet og stabil (typisk navnet på en nedstrøms-tjeneste): breaker-instansen caches for klientens levetid per distinkt navn, så ikke utled navnet fra host, tenant eller request-id.
Default-predikatet er `CircuitBreakerOnRetryableErrors`, dvs. de samme forbigående feilene som er retryable (`Timeout`, `NetworkError`, og statusene `408`/`425`/`429`/`500`/`502`/`503`/`504`).
Permanente feil som `404`, valideringsfeil, serialiseringsfeil og deserialiseringsfeil teller ikke mot circuit breakeren.

For tidsvindu finnes også sliding-window-strategi:

```kotlin
val config = CircuitBreakerConfig.slidingWindow(
    name = "min-nedstroem-window",
    maxFailures = 10,
    windowDuration = 1.minutes,
    resetTimeout = 30.seconds,
)
```

Per-request override fungerer som for `retryConfig`:

```kotlin
val response = klient.get<String>(URI.create("https://example.com/api")) {
    circuitBreakerConfig = CircuitBreakerConfig.count(
        name = "example-api",
        maxFailures = 2,
        resetTimeout = 10.seconds,
    )
}
```

Circuit breaker-state er lokal for én `HttpKlient`-instans.
Det finnes ingen statisk/global state.
Hvis samme `name` brukes flere ganger på samme klient, deles state for den navngitte breakeren i klientinstansen.
Dette unngår at nye, semantisk like lambdaer i inline config lager hver sin breaker.

Ved åpen breaker returnerer klienten `HttpKlientError.CircuitBreakerOpen` med `metadata.attempts = 0`, siden ingen HTTP-forsøk ble utført.

`CircuitBreakerConfig.Enabled` eksponerer åpningsstrategien via vår egen `CircuitBreakerOpeningStrategy` (`Count`/`SlidingWindow`) og `TimeSource` direkte.
Strategitypen er tidskilde-fri, slik at `timeSource` på `CircuitBreakerConfig.Enabled` er den eneste tidskilden.
Den mappes til Arrow sin `CircuitBreaker.OpeningStrategy` først i `toCircuitBreaker()`.

### Samspill med retry

Circuit breakeren ligger utenpå retry-eksekveringen.
Det betyr at retry først får bruke sitt budsjett, og deretter vurderer circuit breakeren sluttresultatet.
Et kall som lykkes etter retry teller derfor ikke som circuit breaker-feil, mens et kall som ender med retryable feil etter at retry-budsjettet er brukt opp teller én gang.

## Auth-token

`HttpKlient` støtter både klient-nivå og per-request bearer-token basert på `AccessToken` fra `common`.
Klienten setter `Authorization: Bearer <token>` automatisk hvis ikke konsumenten allerede har satt `Authorization`-headeren eksplisitt.

Klient-nivå (kalles foran hver request — egnet for `texas`/Token-X-flyter):

```kotlin
val klient: HttpKlient = HttpKlient(clock = clock) {
    authTokenProvider = object : AuthTokenProvider {
        override suspend fun hentToken(skipCache: Boolean): AccessToken =
            tokenService.systemToken("api://app-x", skipCache = skipCache)
    }
}
```

`AuthTokenProvider` er bevisst et vanlig interface (ikke en typealias eller `fun interface`) slik at eksisterende wiring må implementere `hentToken` og navngi `skipCache` når `libs` bumpes — i stedet for at en gammel parameterløs lambda stille kompilerer videre med en ignorert `it`.

`hentToken` kalles med `skipCache = false` på det første forsøket.
Hvis serveren svarer med en status i `skipCacheRetryStatuses` (default kun `401`), gjør klienten _ett_ nytt forsøk der `hentToken` kalles med `skipCache = true`, slik at et cachet, men avvist, token kan byttes ut med et ferskt.
`403` er bevisst ikke med i default (ofte et persistent tilgangsavslag som ville doblet trafikken uten å hjelpe) — sett `skipCacheRetryStatuses = setOf(401, 403)` for å opt-e inn, eller `emptySet()` for å slå retryen av.
Feiler også det andre forsøket, logges en diagnostikkmelding via `loggingConfig` på `skipCacheRetryNivå` (default `WARN`) slik at klienter der dette skjer i loop-aktig volum kan oppdages — konsekvent styrt av samme logging-config som resten av modulen (sett `skipCacheRetryNivå = HttpKlientLogNivå.OFF` for å slå den av).

Per-request (overstyrer klient-default):

```kotlin
val response = klient.get<MinDto>(URI.create("https://example.com/api")) {
    bearerToken(innkommendeAccessToken)
}
```

Hvis `authTokenProvider` kaster, returneres `HttpKlientError.AuthError` (ikke-retryable) og _ingen_ HTTP-kall blir gjort. `metadata.attempts` er `0` for denne feiltypen.

## Redirects

Den underliggende `java.net.http.HttpClient` følger ikke redirects som standard ([HttpClient.Redirect.NEVER]), slik at `3xx`-svar dukker opp eksplisitt som `UventetStatus` i stedet for å bli fulgt stille.
Sett `followRedirects` på klient-configen for å endre dette:

```kotlin
val klient = HttpKlient(clock = clock) {
    followRedirects = HttpClient.Redirect.NORMAL
}
```

## Observability / metrikker

`httpklient` eksponerer ikke egne metrikker.
I NAIS-tjenester gir auto-instrumentering allerede HTTP-klient-metrikker (latency, status, antall kall) uten kode i biblioteket:

```yaml
observability:
  autoInstrumentation:
    enabled: true
    runtime: java
```

Trenger du domenespesifikke tellere (f.eks. per nedstrøms-tjeneste eller per retry-forbruk), kan du bruke de eksisterende hookene: `onExcessiveRetries` på `RetryConfig` og `doOnOpen` / `doOnClosed` / `doOnHalfOpen` / `doOnRejectedTask` på circuit breaker-configen, samt `metadata.attempts` / `metadata.totalDuration` på hvert svar.

## Begrensninger og videre arbeid

- **Binære bodyer / nedlasting**: requests og responser håndteres i dag kun som `String` (`BodyHandlers.ofString` / `BodyPublishers.ofString`).
  Fil- og byte-baserte over-/nedlastinger er TODO og legges til ved behov.
- **`Retry-After`**: klienten respekterer foreløpig ikke `Retry-After`-headeren på `429`/`503`; backoff styres kun av den konfigurerte `Schedule`.
  Å lese `Retry-After` for retryable responser er TODO.

