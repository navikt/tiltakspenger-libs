# httpklient

`httpklient` er en liten felles HTTP-klient basert på Java sin innebygde `java.net.http.HttpClient`.

Målet er å standardisere enkel HTTP-bruk i tiltakspenger-libs med:

- Arrow `Either<HttpKlientError, HttpKlientResponse<T>>` i stedet for exceptions i API-et (suksess-grenen er en `HttpKlientResponse<T>` med status, body og metadata).
- Egne venstresider for blant annet `Timeout`, `NetworkError`, `InvalidRequest`, `UventetStatus`, `SerializationError`, `DeserializationError` og `CircuitBreakerOpen`, gruppert i `RequestIkkeSendt`, `IngenRespons` og `ResponsMottatt` etter «så serveren requesten min?».
- Én statisk typet metode per reelt behov, i stedet for en generisk builder: `Content-Type`, `Accept` og (de)serialisering er en konsekvens av hvilken metode du kaller.
- DTO-er inn/ut via `tiltakspenger-libs/json` sine `serialize`/`deserialize`-hjelpere.
- Felles `HttpKlientMetadata` på både `HttpKlientResponse` og `HttpKlientError` med rå request/response, headere, tidsstempler og status der de finnes.
- `Statusregel` som ren data for hvilke HTTP-statuser som regnes som suksess.
- Valgfri resilience via retry og circuit breaker basert på Arrow Resilience.
- Klienten logger aldri selv; konsumenten logger én gang per hendelse med `HttpKlientError.loggFeil` / `HttpKlientResponse.loggSuksess`.

## API-et

`HttpKlient` er en `final` klasse uten interface — det eneste som kan byttes ut er `HttpTransport`.
All konfigurasjon er ren data i `HttpKlientConfig` (timeout, `KlientAuth`, `Retry`, `CircuitBreakerConfig`, skip-cache-statuser).
Det finnes ingen per-kall-overstyringer: et endepunkt med avvikende behov får en egen klientinstans.

```kotlin
val klient = HttpKlient(
    clock = clock,
    config = HttpKlientConfig(
        timeout = 10.seconds,
        auth = KlientAuth.System(authTokenProvider),
        retry = Retry.Fast(maksForsøk = 4, delay = 1.seconds),
    ),
    transport = JavaHttpTransport(connectTimeout = 5.seconds),
)
```

| Behov | Metode |
|---|---|
| JSON inn/ut | `getJson<T>`, `postJson<T>` (DTO eller `SerialisertJson`) |
| JSON ut, `null` på gitte statuser | `getJsonEllerNull<T>`, `postJsonEllerNull<T>` |
| JSON inn, kun status ut | `postJsonUtenSvar`, `putJsonUtenSvar`, `patchJsonUtenSvar` |
| PDF ut | `postJsonMotPdf`, `getPdf` |
| Binært inn (bilde mot pdfgen) | `postBytesMotPdf(bytes, contentType)` |
| Filopplasting | `postMultipart<T>(deler: MultipartDeler)` |
| Rå tekst inn | `postTekst<T>(tekst, sensitiv)` |
| `application/x-www-form-urlencoded` inn | `postForm<T>(felter)` |

Ferdigserialisert JSON sendes med `SerialisertJson`-wrapperen (aldri en `String`-overload), typisk når nøyaktig denne payloaden skal persisteres sammen med resultatet.
Egne request-headere settes med `Header`/`NavHeadere`; de reserverte navnene (`Content-Type`, `Accept`, `Authorization`, `Content-Length`, `Host`) eies av klienten og avvises fail-fast.

```kotlin
val respons = klient.postJson<SaksnummerResponse>(
    uri = URI.create("$baseUrl/saksnummer"),
    body = FnrDTO(fnr.verdi),
    headere = listOf(NavHeadere.navCallId(correlationId.toString())),
    godta = Statusregel.Eksakt(200),
)
```

### Binære bodyer og sikkerlogg

Binært innhold dekodes aldri som tekst, verken inn eller ut.
`metadata.rawResponseString` blir `<binær respons, N bytes>`, en `postBytesMotPdf`-request blir `<binær body, N bytes, image/png>`, og en multipart-request gjengis som struktur og størrelser:

```
<multipart/form-data, 2 deler>
<binær del 'file0' (vedlegg.png), 4711 bytes, image/png>
<binær del 'file1' (dok.pdf), 91234 bytes, application/pdf>
```

Det gjør at metadataen alltid trygt kan sendes til sikkerlogg.
`MultipartDel` avviser CR/LF i feltnavn, filnavn og `Content-Type` (header-injeksjon), mens anførselstegn og backslash i feltnavn og brukeropplastede filnavn escapes ved enkoding i stedet for å velte kallet.
Delene sendes som `MultipartDeler` — en `Nel`-basert samletype som eier invariantene «minst én del», «unike feltnavn» og «unike filnavn», i stedet for at hvert kallsted gjentar dem.
Bygger du delene med `mapIndexed` o.l., konverter med `tilMultipartDeler()`.

Kravet om unike filnavn er ikke kosmetikk: NAIS-antivirus nøkler skanneresultatene på filnavn (`files[header.Filename] = buf`), så to deler med samme filnavn kollapser til én oppføring, og den ene filen blir aldri skannet.
Unike feltnavn hjelper ikke mot dette — feltnavnet forsvinner hos mottakeren.
Har kallstedet filnavn det ikke kontrollerer, som brukeropplastede vedlegg, må det gjøre dem unike selv før delene bygges.
`tiltakspenger-soknad-api` gjør det med et indeksprefiks (`cv.pdf` → `0-cv.pdf`): prefiks framfor suffiks lar filendelsen bli stående sist, og navnene blir garantert unike siden indeksen er rene siffer avsluttet med bindestrek.

Escapingen er quoted-pair (`\"`, `\\`), ikke prosentkoding som nettlesere og OkHttp bruker.
Det er verifisert mot den eneste mottakeren vi har: NAIS-antivirus ([`nais/clamav-rest`](https://github.com/nais/clamav-rest)) parser med Go sin `mime/multipart`, som unescaper quoted-pair.
Prosentkoding ville gitt filnavn verbatim tilbake som `cv%22.pdf` i skanneresultatet.
Legger du til en mottaker med en annen parser, sjekk dette punktet på nytt.

#### Størrelse er kallstedets ansvar

`httpklient` håndhever ingen størrelsesgrense på request-bodyer.
Den enkodede multipart-bodyen materialiseres i minnet i tillegg til de `ByteArray`-ene konsumenten allerede holder — grovt 2-3× total filstørrelse per samtidige request.
Videresender du brukeropplastede vedlegg uten egen kontroll, holder N samtidige opplastinger à M MB til å ta ned poden.
Grensen hører hjemme på kallstedet, som kjenner både forventet filstørrelse og hvor mange samtidige opplastinger tjenesten skal tåle.
Nedstrøms finnes det gjerne en grense i tillegg — NAIS-antivirus svarer `413` med `file size exceeds limit` — men den beskytter mottakeren, ikke oss.

`MultipartDel` og `postBytesMotPdf` kopierer heller ikke `ByteArray`-en de får inn, av samme minnehensyn.
De låner kallerens array; muterer du den etter at delen er konstruert, går det muterte innholdet på wire.

### Response-typer og tomme bodyer

En tom body kan ikke deserialiseres til en DTO.
Et `204`-svar med en DTO som response-type gir derfor `HttpKlientError.DeserializationError` med en feilmelding som peker videre.
Bruk `getJsonEllerNull`/`postJsonEllerNull` med `nullVedStatus`, eller en `UtenSvar`-variant, for endepunkter som kan svare uten body.

En `Statusregel.Eksakt` som godtar `204`/`205` avvises fail-fast når bodyen faktisk skal deserialiseres, siden RFC 9110 garanterer at de statusene er uten body.
Det gjelder også `postTekst`/`postForm`/`postMultipart`, men kun for DTO-responstyper: `postTekst<Unit>(godta = Statusregel.Eksakt(204))` er lovlig og riktig for endepunkter som tilgangsmaskinen, som svarer `204` uten innhold.

`getJson<String>`, `getJson<Unit>` og `getJson<ByteArray>` er bevisst ulovlige og feiler fail-fast: rå respons-tekst finnes alltid i `metadata.rawResponseString`, `Unit` har egne `UtenSvar`-varianter, og binært har `getPdf`/`postJsonMotPdf`.

## Testing

Tester bytter ut `HttpTransport` med `FakeHttpTransport` fra modulens testFixtures:

```kotlin
testImplementation(testFixtures("com.github.navikt.tiltakspenger-libs:httpklient-infrastruktur:$felleslibVersion"))
```

Da kjører hele den reelle pipelinen — auth-materialisering, retry-gates, statusregler, Jackson-deserialisering, metadata og maskering — og bare nettverket er borte.
En køet `500` gir dermed `Left(UventetStatus)` fordi produksjonens statusregel faktisk evalueres, ikke fordi faken emulerer den.

```kotlin
val fnr = FnrGenerator().generer()
val transport = FakeHttpTransport()
transport.leggIKøJson(SaksnummerResponse(saksnummer = "202501011001"))

val klient = MinKlient(baseUrl = "http://localhost", clock = fixedClock, transport = transport)
klient.hentSaksnummer(fnr).getOrFail().saksnummer shouldBe "202501011001"

transport.mottatteKall.single().bodyTekst shouldBe """{"fnr":"${fnr.verdi}"}"""
```

Køen er FIFO uavhengig av URI, og retry konsumerer ett køet svar per forsøk.
Tom kø kaster `AssertionError` med metode og URI, slik at et manglende testoppsett feiler høylytt.
Transportfeil simuleres med `leggIKøKast` og JDK-exceptions (`HttpTimeoutException` → `Timeout`, `IOException` → `NetworkError`).

Hver klient bør i tillegg ha én test som bygger default-`HttpKlient`-oppsettet mot WireMock, slik at også produksjonstransporten og den reelle URL-byggingen er dekket.

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
- `requestHeaders` — effektive request-headere, med _umaskerte_ verdier (en bearer-token ligger her i klartekst).
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
Selve HTTP-requesten sendes med de ekte verdiene, og det strukturerte `requestHeaders`-mappet beholder også de umaskerte verdiene (bruk `rawRequestString` hvis du skal logge).
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

Du kan matche enten på den konkrete varianten eller på gruppen. `UventetStatus` het tidligere `Ikke2xx`; navnet er endret fordi feilen egentlig betyr «status ble ikke godtatt av kallets `Statusregel`», ikke bokstavelig «utenfor 2xx».

### Logging

Klienten logger aldri selv.
Konsumenten logger nøyaktig én gang per hendelse, fra laget som har domenekonteksten (typisk en service), med hjelperne på feil- og responstypen:

```kotlin
return arenaKlient.hentMeldekort(fnr, periode)
    .onLeft { it.loggFeil(logger, "henting av meldekort fra Arena", "Periode: $periode", sikkerlogg) }
    .onRight { it.loggSuksess(logger, "Hentet meldekort fra Arena.", sikkerlogg) }
    .map { it.body }
```

`loggFeil` henter all HTTP-kontekst fra feilen selv, så kalleren bidrar bare med det den vet mer om enn klienten: hva som ble forsøkt (`operasjon`) og hvilket domeneobjekt det gjaldt (`kontekst`).
`logger` er kallerens egen logger, slik at logglinja får kallerens navnerom.
`sikkerlogg` defaulter til companion-objektet, som gir en ren tekst-henvisning; injiser appens `KotlinLoggingSikkerlogg(appNavn, gcpProsjektId)` for å få en klikkbar lenke til sikkerloggen i logglinja.

At klienten er stille er et bevisst valg: transport-logging fra libs pluss domenelogging fra konsumenten ga to logghendelser for samme feil, og bare konsumenten vet hva kallet betød.
Konsumentens egen klientklasse skal derfor heller ikke logge — den returnerer `Either`, og laget som håndterer feilen logger den én gang.

### Vanlig logg vs. Sikkerlogg (PII)

Vanlig `logger` skal aldri inneholde PII, mens `Sikkerlogg` kan.
`loggFeil` og `loggSuksess` håndhever delingen:

- **Vanlig logg** får `operasjon`, `kontekst`, feilartens `beskrivelse`, `endepunkt`, `attempts`, `totalDuration`, `statusCode` når serveren svarte, og henvisningen til sikkerloggen — ingenting fra selve requesten eller responsen.
  `loggSuksess` setter i tillegg `tidsgrenser.svar` ved siden av varigheten, slik at «brukt: 4.8s av 5s per forsøk» blir en tidlig varsling lenge før kallet begynner å time ut.
- **Sikkerlogg** får i tillegg `rawRequestString`, `rawResponseString` og `responseHeaders`.
- **Sensitive headere** (`Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`) er allerede maskert til `***` i `rawRequestString`, så heller ikke sikkerloggen ser bearer-tokens.
- **Binært innhold** gjengis som `<binær respons, N bytes>` og lignende, aldri som dekodet tekst.

Kalleren styrer altså PII-grensen gjennom `kontekst`-strengen: den havner i vanlig logg, så bruk ID-er (`sakId`, saksnummer, periode) og ikke fødselsnummer eller navn.
Skriver du en egen logglinje ved siden av, bruk `metadata.rawRequestString` — ikke `metadata.requestHeaders`, som har umaskerte verdier.

### `UriSynlighet`: den ene vurderingen klienten må gjøre selv

URIen er ofte den mest nyttige konteksten i en feillogg, men den kan bære en ident i en path-variabel eller et query-parameter.
`httpklient` kan ikke vite hvilket av tilfellene det er, så klienten tar stilling én gang i `HttpKlientConfig`:

| Verdi | `metadata.endepunkt` blir | Når |
| --- | --- | --- |
| `KunSikkerlogg` (default) | `POST https://host/<skjult>` | Klienten har ikke tatt stilling, eller path/query kan bære personopplysninger. Hele URIen finnes i `rawRequestString`, altså i sikkerlogg. |
| `VanligLogg` | `POST https://host/full/sti` | Faste endepunkter der identen ligger i request-bodyen — skjermings- og PDL-klientene er eksempler. |

Defaulten er den trygge antagelsen, men den avkortede formen navngir fortsatt hvilken integrasjon som feilet, siden host aldri er en personopplysning.

### Hvorfor `beskrivelse` og `endepunkt` står i meldingen

Feil fra `java.net.http` oppstår asynkront: exceptionen lages på klientens `SelectorManager`-tråd, ikke på tråden som gjorde kallet.
Stacktracen inneholder derfor ingen applikasjonsframes og kan hverken fortelle hvilket endepunkt eller hvilken flyt det gjaldt — en `HttpConnectTimeoutException` ser identisk ut uansett hvem den gjaldt.
Alt som skal være til hjelp i prod må derfor stå i selve logglinja.

Av samme grunn skiller `HttpKlientError.Timeout` på `Timeoutfase.Oppkobling` og `Timeoutfase.Svar`.
JDK-en skiller dem i typen (`HttpConnectTimeoutException` vs. `HttpTimeoutException`), og det er eneste sted skillet finnes: meldingen er «HTTP connect timed out» i begge tilfeller.
En oppkoblings-timeout peker på nettverk, DNS eller en mottaker som ikke tar imot forbindelser; en svar-timeout peker på en treg mottaker.

Fasen avgjøres én gang, i `toAttemptFailure`, der vi oversetter fra JDK-exception til `AttemptOutcome`, og bæres derfra hele veien til `HttpKlientError.Timeout`.
Klassifiserer man ikke ved grensa, må skillet gjettes tilbake fra `throwable` lenger ut i kjeden — spesialisert → generalisert → spesialisert, der mellomtypen kastet informasjon den fikk inn.
`AttemptOutcome` finnes fordi et enkelt forsøk ikke kan være en `HttpKlientError`: den krever `HttpKlientMetadata`, og `attempts`/`attemptDurations`/`totalDuration` er summer som først finnes når retry-loopen er ferdig.
Prisen for den mellomtypen er at hver variant må bære alt den korresponderende feilen trenger.

### Tidsgrensene i metadataen

`HttpKlientMetadata.tidsgrenser` bærer budsjettet kallet kjørte under, fordi en varighet uten grensen ved siden av ikke er til å tolke — «brukt: 1.003s» kan være en klient som akkurat brøt 1 s, eller en som brukte en brøkdel av 30 s.

- `svar` kommer fra `HttpKlientConfig.timeout` og gjelder per forsøk.
- `oppkobling` kommer fra **transporten**, ikke fra config: `connectTimeout` er en egenskap ved selve `HttpClient`-instansen, og en kopi i config ville vært en annen sannhetskilde enn den som faktisk gjelder.
  Den er `null` for transporter som ikke kobler opp noe, altså testfakes.

Merk at grensene gjelder per forsøk, mens `totalDuration` dekker alle forsøk pluss backoff — de to skal ikke sammenlignes direkte når `attempts > 1`.

## Suksess-statuser

`Statusregel` er ren data, ikke et predikat, slik at regelen kan sammenlignes, logges og asserters i tester.
Default er `Statusregel.Alle2xx`.
Endepunkter med avvikende kontrakt setter regelen per kall med `godta`:

```kotlin
val respons = klient.postJson<UtbetalingResponse>(
    uri = URI.create("$baseUrl/utbetaling"),
    body = request,
    godta = Statusregel.Eksakt(202),
)
```

En status som ikke godtas gir `HttpKlientError.UventetStatus` med lesbar body og full metadata.
Statuser som betyr noe *annet* enn suksess hører ikke hjemme i `Eksakt` — tilgangsmaskinens `403` og dokarkivs dedup-`409` leses fra feiltypen med `harStatus`/`bodySomJson`, slik at suksesskanalen beholder én type.

## Retry

Retry er ren data på klientens config, og default er `Retry.Ingen` — retry er en aktiv beslutning per klient, ikke noe man får stille.

```kotlin
val klient = HttpKlient(
    clock = clock,
    config = HttpKlientConfig(
        retry = Retry.Standard(maksForsøk = 3, grunnDelay = 250.milliseconds, maksDelay = 2.seconds),
    ),
    transport = transport,
)
```

| Variant | Oppførsel |
|---|---|
| `Retry.Ingen` | Ingen retries (default). Riktig der konsumenten selv eier feilhåndteringen, f.eks. utbetaling. |
| `Retry.Fast(maksForsøk, delay)` | Konstant delay uten jitter. Finnes for paritet med appene som kjørte «N forsøk med konstant 1 s» på ktor-klienten — en migrering rett til `Standard` ville stille byttet dem til eksponentiell backoff. |
| `Retry.Standard(maksForsøk, grunnDelay, maksDelay)` | Eksponentiell backoff med moderat symmetrisk jitter (0.5–1.5), hardt cappet på `maksDelay`. |

`maksForsøk` teller totalt antall forsøk inkludert det første, så `Fast(maksForsøk = 4)` tilsvarer ktor-ens `retryOnServerErrors(3)`.

To gates er harde og kan ikke konfigureres bort:

- Et utfall som ikke er retryable forsøkes aldri på nytt (se tabellen under).
- `POST` og `PATCH` retryes aldri som standard: når et forsøk feiler uten respons, er det ukjent om serveren rakk å behandle requesten, og et nytt forsøk kan gi doble sideeffekter.
  `GET` og `PUT` regnes som idempotente (RFC 9110 §9.2.2).
  Endepunkter med dedup — dokarkiv svarer `409` på duplikater — kan opt-e inn med `retryIkkeIdempotente = true`.

En respons som godtas av `Statusregel` retryes aldri, selv om statuskoden er i den retryable mengden (f.eks. hvis en konsument bevisst godtar `503` som suksess).

`HttpKlientMetadata` får alltid med `attempts`, `attemptDurations` og `totalDuration`, både på vellykkede svar og på alle `HttpKlientError`-varianter, så forbruket av tid og forsøk kan leses rett av svaret.

### Retryable-flagg

Hver `HttpKlientError` eksponerer `retryable: Boolean`.
Retry-loopen bruker dette som en **hard gate** — den vil aldri forsøke på nytt for utfall som regnes som permanente:

| Variant | `retryable` |
|---|---|
| `Timeout`, `NetworkError` | `true` |
| `UventetStatus` med status `408`, `425`, `429`, `500`, `502`, `503`, `504` | `true` |
| `UventetStatus` med øvrige statuser | `false` |
| `InvalidRequest`, `SerializationError`, `DeserializationError`, `AuthError`, `CircuitBreakerOpen` | `false` |

Dvs. verken en validerings- eller deserialiseringsfeil eller en `404` blir forsøkt på nytt, uansett hvilken `Retry`-variant klienten er konfigurert med.

## Circuit breaker

`HttpKlient` har valgfri circuit breaker-støtte basert på Arrow Resilience `CircuitBreaker`.
Default er `CircuitBreakerConfig.None`, dvs. ingen circuit breaker.

Configen bygges fluent og settes på klientens `HttpKlientConfig`:

```kotlin
val klient = HttpKlient(
    clock = clock,
    config = HttpKlientConfig(
        circuitBreaker = CircuitBreakerConfig.count(
            name = "min-nedstroem",
            maxFailures = 5,
            resetTimeout = 30.seconds,
        ).withExponentialBackoff(
            factor = 2.0,
            maxResetTimeout = 5.minutes,
        ).doOnOpen {
            meterRegistry.counter("nedstrom_circuit_breaker_open").increment()
        },
    ),
    transport = transport,
)
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

Circuit breaker-state er lokal for én `HttpKlient`-instans.
Det finnes ingen statisk/global state.
Trenger to endepunkter hver sin breaker, får de hver sin klientinstans — på samme måte som for de øvrige configene.

Ved åpen breaker returnerer klienten `HttpKlientError.CircuitBreakerOpen` med `metadata.attempts = 0`, siden ingen HTTP-forsøk ble utført.

`CircuitBreakerConfig.Enabled` eksponerer åpningsstrategien via vår egen `CircuitBreakerOpeningStrategy` (`Count`/`SlidingWindow`) og `TimeSource` direkte.
Strategitypen er tidskilde-fri, slik at `timeSource` på `CircuitBreakerConfig.Enabled` er den eneste tidskilden.
Den mappes til Arrow sin `CircuitBreaker.OpeningStrategy` først i `toCircuitBreaker()`.

### Samspill med retry

Circuit breakeren ligger utenpå retry-eksekveringen.
Det betyr at retry først får bruke sitt budsjett, og deretter vurderer circuit breakeren sluttresultatet.
Et kall som lykkes etter retry teller derfor ikke som circuit breaker-feil, mens et kall som ender med retryable feil etter at retry-budsjettet er brukt opp teller én gang.

## Auth-token

`HttpKlient` støtter både klient-nivå og per-kall bearer-token basert på `AccessToken` fra `common`.
Klienten setter `Authorization: Bearer <token>` automatisk hvis ikke konsumenten allerede har satt `Authorization`-headeren eksplisitt.

Klient-nivå settes med `KlientAuth` på configen (`KlientAuth.Ingen` er default — riktig for pdfgen, ClamAV og leader-elector).
`KlientAuth.System` kaller provideren foran hver request, og er den som passer `texas`-flyter:

```kotlin
val klient = HttpKlient(
    clock = clock,
    config = HttpKlientConfig(
        auth = KlientAuth.System(
            object : AuthTokenProvider {
                override suspend fun hentToken(skipCache: Boolean): AccessToken =
                    tokenService.systemToken("api://app-x", skipCache = skipCache)
            },
        ),
    ),
    transport = transport,
)
```

`AuthTokenProvider` er bevisst et vanlig interface (ikke en typealias eller `fun interface`) slik at eksisterende wiring må implementere `hentToken` og navngi `skipCache` når `libs` bumpes — i stedet for at en gammel parameterløs lambda stille kompilerer videre med en ignorert `it`.

`hentToken` kalles med `skipCache = false` på det første forsøket.
Hvis serveren svarer med en status i `skipCacheRetryStatuses` (default kun `401`), gjør klienten _ett_ nytt forsøk der `hentToken` kalles med `skipCache = true`, slik at et cachet, men avvist, token kan byttes ut med et ferskt.
`403` er bevisst ikke med i default (ofte et persistent tilgangsavslag som ville doblet trafikken uten å hjelpe) — sett `skipCacheRetryStatuses = setOf(401, 403)` på configen for å opt-e inn, eller `emptySet()` for å slå retryen av.
Status leses kun fra et resultat som ble regnet som feil, så en konsument som bevisst godtar `401` som suksess via `Statusregel.Eksakt` får aldri et uventet ekstra kall.
Feiler også det andre forsøket, kommer det ingen egen logglinje fra klienten — feilen returneres som vanlig, og konsumenten logger den én gang med `loggFeil`.

Per kall (overstyrer alltid klient-nivået, typisk OBO-tokens som veksles per saksbehandler):

```kotlin
val respons = klient.getJson<MinDto>(
    uri = URI.create("$baseUrl/api"),
    bearerToken = innkommendeAccessToken,
)
```

Hvis `hentToken` kaster, returneres `HttpKlientError.AuthError` (ikke-retryable) og _ingen_ HTTP-kall blir gjort. `metadata.attempts` er `0` for denne feiltypen.

## Redirects

Redirects følges aldri.
`JavaHttpTransport` bygger den underliggende `java.net.http.HttpClient` med `HttpClient.Redirect.NEVER`, slik at `3xx`-svar dukker opp eksplisitt som `UventetStatus` i stedet for å bli fulgt stille.
Det er ikke konfigurerbart — ingen konsument bruker redirects, og en tjeneste som begynner å svare `3xx` er noe konsumenten bør se, ikke noe klienten skal skjule.

## Observability / metrikker

`httpklient` eksponerer ikke egne metrikker.
I NAIS-tjenester gir auto-instrumentering allerede HTTP-klient-metrikker (latency, status, antall kall) uten kode i biblioteket:

```yaml
observability:
  autoInstrumentation:
    enabled: true
    runtime: java
```

Trenger du domenespesifikke tellere (f.eks. per nedstrøms-tjeneste eller per retry-forbruk), kan du bruke circuit breaker-hookene `doOnOpen` / `doOnClosed` / `doOnHalfOpen` / `doOnRejectedTask`, samt `metadata.attempts` og `metadata.totalDuration` på hvert svar.

## Begrensninger og videre arbeid

- **Filbaserte over-/nedlastinger**: binære bodyer støttes i begge retninger, men alltid i minnet som `ByteArray`, og uten størrelsesgrense i biblioteket — se [Størrelse er kallstedets ansvar](#størrelse-er-kallstedets-ansvar).
  Streaming rett til eller fra fil (`BodyPublishers.ofFile` / `BodyHandlers.ofFile`) er TODO og legges til ved behov.
- **`Retry-After`**: klienten respekterer foreløpig ikke `Retry-After`-headeren på `429`/`503`; backoff styres kun av den konfigurerte `Schedule`.
  Å lese `Retry-After` for retryable responser er TODO.
