# json

Felles Jackson-konfigurasjon og hjelpefunksjoner for serialisering/deserialisering av JSON
i tiltakspenger-prosjektene. Bruk denne modulen, ikke en egen `ObjectMapper`.

## Hovedinnhold

- `objectMapper` — standard `JsonMapper` som håndhever [`BANLIST`](src/main/kotlin/json/Banlist.kt)
  for både serialisering og deserialisering. Bruk denne overalt med mindre du har en eksplisitt grunn til noe annet.
- `objectMapperUtenBanlist` — rømningsluke uten banlist. Bruk kun bevisst (testing/migrering).
- `serialize` / `deserialize` / `deserializeList` / `deserializeMap` (+ `*Nullable`-varianter)
  — typesikre topp-nivå-helpere.
- `lesTre` — parser JSON til `JsonNode` uten å binde til konkrete typer.

## Banlist

`objectMapper` nekter å (de)serialisere visse typer — de kaster `IllegalArgumentException`
(eller en Jackson-wrapper rundt den) på **rot-nivå og alle under-nivåer** (felt i DTO,
element i List/Set, verdi/nøkkel i Map, vilkårlig dypt nestet).

Banlisten finnes i `BANLIST` i [`Banlist.kt`](src/main/kotlin/json/Banlist.kt) og er
sannheten — denne lista er en informativ kopi:

| Type | Hvorfor |
|---|---|
| `arrow.core.Either` | Ikke støttet i JSON. Pakk ut verdien (fold/getOrElse) før serialisering, eller deserialiser til underliggende type og bygg Either-verdien i koden. |
| `arrow.core.Option` | Bruk nullable eller eksplisitt DTO. Either er også bannlyst i JSON; bruk evt. Either internt og pakk ut før serialisering. |
| `java.util.Optional` | Bruk nullable eller eksplisitt DTO. Either er også bannlyst i JSON; bruk evt. Either internt og pakk ut før serialisering. |
| `kotlin.Pair`, `kotlin.Triple` | `first`/`second`/`third` har ingen domenebetydning — bruk navngitt data class. |
| `arrow.core.Tuple4..Tuple9` | Bruk navngitt data class. |
| `kotlin.Result` | `success(v)` serialiseres som `v` (silent unwrap) — uforutsigbart. Pakk ut til en konkret DTO/underliggende type før serialisering. |
| `kotlin.sequences.Sequence` | Lat evaluering konsumerer kilden — konverter til `List` først. |
| `kotlin.collections.Iterator` (= `java.util.Iterator`) | Engangs-konsum, ikke data. |
| `java.util.stream.BaseStream` (Stream/IntStream/LongStream/DoubleStream) | Lat + engangs-konsum. |
| `java.lang.Throwable` | Jackson dumper intern tilstand og stacktrace — serialiser en domenefeilmelding. |
| `java.time.Clock` | Infrastruktur, ikke data — serialiser `Instant.now(clock)` i stedet. |
| `java.lang.Thread` | Infrastruktur med intern tilstand. |
| `java.io.File` | Serialiseres som ren sti-streng — bruk `String` for filstier. |
| `kotlin.text.Regex` | Jackson serialiserer interne felter — bruk `String` for mønsteret. |

Banlistmeldingen skal forklare *hva man bør gjøre i stedet* — ikke bare at typen er bannlyst.

Hvis du legger til, fjerner eller endrer en entry i `BANLIST`: oppdater tabellen over i
samme commit.

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

