# httpklient

`httpklient` er en liten felles HTTP-klient basert på Java sin innebygde `java.net.http.HttpClient`.

Målet er å standardisere enkel HTTP-bruk i tiltakspenger-libs med:

- Arrow `Either<HttpKlientError, T>` i stedet for exceptions i API-et.
- Egne venstresider for blant annet `Timeout`, `NetworkError`, `InvalidRequest`, `Ikke2xx`, `SerializationError`, `DeserializationError` og `CircuitBreakerOpen`.
- Støtte for JSON som `String` inn/ut.
- Støtte for DTO-er inn/ut via `tiltakspenger-libs/json` sine `serialize`/`deserialize`-hjelpere.
- Suspend-first API med en Ktor-inspirert `RequestBuilder`.
- Verb-hjelpere for `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD` og `OPTIONS`.
- Felles `HttpKlientMetadata` på både `HttpKlientResponse` og `HttpKlientError` med rå request/response, headere og status der de finnes.
- Konfigurerbar definisjon av hvilke HTTP-statuser som regnes som suksess.
- Valgfri resilience via retry og circuit breaker basert på Arrow Resilience.
- Valgfri logging via vanlig `KLogger` og/eller `Sikkerlogg`.

## Eksempel

Bruk `HttpKlient` som avhengighetstype i konsumenter. Produksjonsklienten opprettes via
`HttpKlient(clock = ...)`-factoryen (den konkrete implementasjonen er intern). Tester kan bruke
`HttpKlientFake` fra `test-common`.

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

For JSON som allerede er en `String`:

```kotlin
val response = klient.post<String>(URI.create("https://example.com/api")) {
    json("""{"id":"123"}""")
}
```

For raw tekst uten JSON-headere:

```kotlin
val response = klient.post<String>(URI.create("https://example.com/api")) {
    header("Content-Type", "text/plain")
    body("hei")
}
```

## Testing

`test-common` eksponerer `HttpKlientFake`, en enkel fake som implementerer `HttpKlient`,
tar opp alle requests som `HttpKlientRequest`, og svarer med køede responser/feil:

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

`HttpKlientResponse` har alltid `headers` fra HTTP-responsen og `requestHeaders` slik requesten faktisk ble sendt etter at klienten har lagt til eventuelle standard JSON-headere.

Feiltypene i `HttpKlientError` har også `requestHeaders`, og `responseHeaders` når feilen kommer etter at en HTTP-respons er mottatt, for eksempel `Ikke2xx` og `DeserializationError`.

Både vellykkede svar og feil har i tillegg `metadata`:

```kotlin
val metadata: HttpKlientMetadata = response.metadata
```

`HttpKlientMetadata` inneholder:

- `rawRequestString` — klientens tekstrepresentasjon av requesten (ikke garantert byte-for-byte wire-format fra Java `HttpClient`).
- `rawResponseString` — rå response-body når en response finnes.
- `requestHeaders` — effektive request-headere.
- `responseHeaders` — response-headere når en response finnes.
- `statusCode` — HTTP-status når en response finnes.
- `attempts` — antall forsøk som ble utført, inkludert det første. `1` betyr at det ikke ble retry-et.
- `attemptDurations` — varighet per forsøk i den rekkefølgen de ble kjørt.
- `totalDuration` — total veggklokketid for hele kallet (inkludert backoff mellom forsøk).

Left-verdier fyller inn så mye metadata som finnes for feilsituasjonen. For eksempel har `SerializationError` request-informasjon, men ingen response, mens `Ikke2xx` har både request, response-body, response-headere og status.

Logging er av som standard. Den kan skrus på globalt:

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

## Suksess-statuser

Som standard er `2xx` suksess. Dette kan overstyres globalt:

```kotlin
val klient = HttpKlient(clock = clock) {
    successStatus = { status -> status == 202 }
}
```

Eller per request:

```kotlin
val response = klient.get<String>(URI.create("https://example.com/api/cache")) {
    successStatus { status -> status == 200 || status == 304 }
}
```

## Retry

`HttpKlient` har valgfri retry-støtte basert på Arrow Resilience `Schedule`. Default er
`RetryConfig.None`, dvs. ingen retries.

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

`RetryOnServerErrorsAndNetwork` tillater retry kun for idempotente metoder (`GET`, `HEAD`, `OPTIONS`, `PUT`, `DELETE`). Hvilke utfall som i det hele tatt er retry-bare (`429` / `5xx` / `Timeout` / `NetworkError`) styres av `AttemptOutcome.retryable` som en hard gate før predikatet.

For konstant backoff finnes `RetryConfig.fixed(maxRetries, delay)`. Konsumenter kan også sende inn en Arrow `Schedule<AttemptOutcome, *>` direkte:

```kotlin
val retryConfig = RetryConfig(
    schedule = Schedule.spaced<AttemptOutcome>(200.milliseconds)
        .zipLeft(Schedule.recurs(3)),
).withRetryOn(RetryOnServerErrorsAndNetwork)
```

Per-request override fungerer som for `successStatus` og `loggingConfig`:

```kotlin
val response = klient.post<String>(URI.create("https://example.com/api/idempotent-key")) {
    json(payload)
    retryConfig = RetryConfig.fixed(maxRetries = 2, delay = 100.milliseconds)
}
```

`HttpKlientMetadata` får alltid med `attempts`, `attemptDurations` og `totalDuration`, både på vellykkede svar og på alle `HttpKlientError`-varianter. Disse kan brukes til å få et bilde av hvor mye tid og hvor mange retries et kall faktisk forbrukte.

`onExcessiveRetries` kalles når antall retries (`attempts - 1`) er minst `excessiveRetriesThreshold`. Default-callbacken logger en WARN — bruk `withoutExcessiveRetriesNotification()` for å skru av varslingen igjen, eller `notifyOnExcessiveRetries(threshold) { ... }` for å eksponere som metrikk.

### Retryable-flagg

Hver `HttpKlientError` (og `AttemptOutcome` internt) eksponerer `retryable: Boolean`. Retry-loopen bruker dette som en **hard gate** — den vil aldri forsøke på nytt for utfall som regnes som permanente, uansett hva [RetryConfig.retryOn] returnerer:

| Variant | `retryable` |
|---|---|
| `Timeout`, `NetworkError` | `true` |
| `Ikke2xx` med status `429`, `5xx` | `true` |
| `Ikke2xx` med øvrige statuser (4xx utenom 429, 3xx, 2xx, 1xx) | `false` |
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

`CircuitBreakerConfig.count(name, maxFailures, resetTimeout)` åpner etter `maxFailures` feil som
matcher `failurePredicate`. `name` er den stabile nøkkelen for breaker-state innenfor én
`HttpKlient`-instans. Bruk derfor samme navn for requests som skal dele breaker, også hvis
configen bygges inline per request. Default-predikatet er `CircuitBreakerOnRetryableErrors`, dvs.
de samme forbigående feilene som er retryable (`Timeout`, `NetworkError`, `429` og `5xx`). Permanente
feil som `404`, valideringsfeil, serialiseringsfeil og deserialiseringsfeil teller ikke mot circuit breakeren.

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

Circuit breaker-state er lokal for én `HttpKlient`-instans. Det finnes ingen statisk/global
state. Hvis samme `name` brukes flere ganger på samme klient, deles state for den navngitte
breakeren i klientinstansen. Dette unngår at nye, semantisk like lambdaer i inline config lager
hver sin breaker.

Ved åpen breaker returnerer klienten `HttpKlientError.CircuitBreakerOpen` med
`metadata.attempts = 0`, siden ingen HTTP-forsøk ble utført.

### Samspill med retry

Circuit breakeren ligger utenpå retry-eksekveringen. Det betyr at retry først får bruke sitt
budsjett, og deretter vurderer circuit breakeren sluttresultatet. Et kall som lykkes etter retry
teller derfor ikke som circuit breaker-feil, mens et kall som ender med retryable feil etter at
retry-budsjettet er brukt opp teller én gang.

## Auth-token

`HttpKlient` støtter både klient-nivå og per-request bearer-token basert på `AccessToken` fra `common`. Klienten setter `Authorization: Bearer <token>` automatisk hvis ikke konsumenten allerede har satt `Authorization`-headeren eksplisitt.

Klient-nivå (kalles foran hver request — egnet for `texas`/Token-X-flyter):

```kotlin
val klient: HttpKlient = HttpKlient(clock = clock) {
    authTokenProvider = { tokenService.systemToken("api://app-x") } // AccessToken
}
```

Per-request (overstyrer klient-default):

```kotlin
val response = klient.get<MinDto>(URI.create("https://example.com/api")) {
    bearerToken(innkommendeAccessToken)
}
```

Hvis `authTokenProvider` kaster, returneres `HttpKlientError.AuthError` (ikke-retryable) og _ingen_ HTTP-kall blir gjort. `metadata.attempts` er `0` for denne feiltypen.

