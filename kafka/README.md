# Oppsett av ny kafka-consumer

## Oversikt

Beskrivelse av hvordan man setter opp en ny Kafka-consumer.  
Målet er å lage en consumer som kan lese meldinger fra et Kafka-topic og deserialisere dem (f.eks. Avro).

`JournalposthendelseConsumer` brukes her som et eksempel.

---

## 1. Lage en ny consumer-klasse

- Lag en ny klasse som representerer consumeren din.
- Klassen må implementere interfacet `Consumer<K, V>`.
- Klassen bør ta inn parameterne som trengs for å initialisere consumeren, f.eks.:
    - `topic`: Kafka-topic som skal leses
    - `groupId`: Kafka consumer group
    - `kafkaConfig`: Konfigurasjon for consumeren. Her benytter vi at forskjellige configs basert på om vi kjører i nais
      eller lokalt.

Eksempel:

```kotlin
class JournalposthendelseConsumer(
    topic: String,
    groupId: String = KAFKA_CONSUMER_GROUP_ID,
    kafkaConfig: KafkaConfig = if (Configuration.isNais()) KafkaConfigImpl(autoOffsetReset = "latest") else LocalKafkaConfig(),
) : Consumer<String, JournalfoeringHendelseRecord> {
    // Implementasjon kommer her
}
```

## 2. Konfigurasjon av Kafka-consumer

- Lag en instans av consumeren ved å bruke `ManagedKafkaConsumer`.
- Hvis du bruker Avro, må du bruke `avroConsumerConfig`, ellers `consumerConfig`
    - `valueDeserializer` bør settes til `KafkaAvroDeserializer()` for Avro-meldinger. Ellers kan du bruke
      `StringDeserializer()`.

Eksempel:

````kotlin
private val consumer = ManagedKafkaConsumer(
    topic = topic,
    config = kafkaConfig.avroConsumerConfig(
        keyDeserializer = StringDeserializer(),
        valueDeserializer = KafkaAvroDeserializer(),
        groupId = groupId,
        useSpecificAvroReader = true,
    ),
    consume = ::consume,
)
````

## 3. Implementere konsumering av meldinger

- Implementer metoden `consume` for å håndtere meldinger som leses fra Kafka. 

Eksempel:

````kotlin
override suspend fun consume(key: String, value: JournalfoeringHendelseRecord) {
    if (value.hendelsesType == "JournalpostMottatt" && value.temaNytt == "IND") {
        log.info { "Hendelse er av typen JournalpostMottatt ${value.journalpostId}" }
    }
}
````

## 4. Lag en instans av consumeren i ApplicationContext

- Lag en instans av klassen din i ønskede contexts.

Eksempel med consument i `ApplicationContext.kt`:

```kotlin
val journalposthendelseConsumer by lazy {
    JournalposthendelseConsumer(
        topic = Configuration.topic,
    )
}
```

## 5. Starte consumeren

- Start consumeren ved å kalle `run()`-metoden på instansen din.

Eksempel hvor man starter en consumer i `Application.kt`:

```kotlin
if (Configuration.isNais()) {
    applicationContext.journalposthendelseConsumer.run()
}
```

## Huske regler

Når du setter opp en ny Kafka-consumer, er det noen viktige regler og praksiser du alltid bør følge:

**Tilgang til lesing av meldingene**
- Ofte krever de ulike produsentene av meldinger at du har tilgang til å lese fra topicet. Som oftest må du lage en PR i det respektive
  teamets repo for å få dette til.

**Legge til topics i miljøvariabler**

- Sørg for at Kafka-topic som consumeren skal lese er definert i konfigurasjonen for miljøet.
- Disse blir gjort på litt forskjellige måter i appene våre. Følg appens konvensjon.

**Hente ut Avro-skjemaer**

- For Avro-deserialisering må du ha de riktige `.avsc/.avdl`-filene (schema-definitionene). Disse finner man sikkert
  rundt omkring på dokumentasjonssidene til teamet som produserer meldinger.

**Plassering av Avro-filer**

- Legg `.avsc/.avdl`-filene i prosjektets `src/main/avro`-mappe.

eksempel struktur:

  ```
  src/main/avro/
    JournalfoeringHendelseRecord.avsc
    AndreMeldingRecord.avsc
  ```

## Nyttige tips

- `autoOffsetReset` i `KafkaConfig` bør settes til `"latest"`når man kjører konsumenten for første gang. Dette er fordi
den må 'initalisere' en offsett for første gang. Vi har i visse tilfeller endret til `none` etterpå.
- `AvroSchemaSupport`-pluginen som finnes for Intellij gir deg kule snacks når du jobber med Avro-filer.