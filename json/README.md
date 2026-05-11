# json

Felles Jackson-konfigurasjon og hjelpefunksjoner for serialisering/deserialisering av JSON
i tiltakspenger-prosjektene. Bruk denne modulen, ikke en egen `ObjectMapper`.

## Hovedinnhold

- `objectMapper` — standard `JsonMapper` som håndhever [BANLIST] for både serialisering og
  deserialisering. Bruk denne overalt med mindre du har en eksplisitt grunn til noe annet.
- `objectMapperUtenBanlist` — rømningsluke uten banlist. Bruk kun bevisst (testing/migrering).
- `serialize` / `deserialize` / `deserializeList` / `deserializeMap` (+ `*Nullable`-varianter)
  — typesikre topp-nivå-helpere.
- `lesTre` — parser JSON til `JsonNode` uten å binde til konkrete typer.

## Banlist

Følgende typer kaster `IllegalArgumentException` (eller en Jackson-wrapper rundt den)
ved (de)serialisering: `Either`, `Option`, `Optional`, `Pair`, `Triple`, `Tuple4..9`,
`Result`, `Sequence`, `Throwable`, `Clock`, `Thread`, `File`, `Regex`. Se
`Banlist.kt` for begrunnelser per type.

Banlistmeldingen skal forklare *hva man bør gjøre i stedet* — ikke bare at typen er bannlyst.

## JSON-feltnavn med æøå

Generelle navnekonvensjoner for repoet ligger i [rot-README](../README.md). Det som er
spesifikt for `json`-modulen: Jackson Kotlin-modulen er konfigurert med
`KotlinPropertyNameAsImplicitName` slik at Kotlin property-navn (inkludert æøå og
backtick-navn) bevares som JSON-feltnavn — ingen ekstra `@JsonProperty`-annotering trengs
for `"beløp"`, `"årsak"` osv.

## Eksempler

```kotlin
val json = serialize(MinDto(beløp = BigDecimal("100.50")))
val dto: MinDto = deserialize(json)

val liste: List<MinDto> = deserializeList(jsonArray)
val map: Map<String, MinDto> = deserializeMap(jsonObject)

// Rømningsluken — bruk kun bevisst:
val par = serialize("a" to 1, enforceBanlist = false)
```

