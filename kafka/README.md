# kafka

Kafka-oppsett for appene: `KafkaConfig` bygger konfigurasjonsmappene, `ManagedKafkaConsumer` kjører konsument-loopen og `Producer` produserer meldinger.
Alt ligger i pakka `no.nav.tiltakspenger.libs.kafka.infra`, siden Kafka er infrastruktur og aldri skal importeres fra et domenelag.
Avro-topics krever i tillegg modulen `kafka-avro`.

## KafkaConfig

`KafkaConfig` er en final klasse med ren data og uten miljølesing: brokeradresse, `autoOffsetReset` og et `KafkaSikkerhet`-valg.
Det finnes ingen egne klasser for nais, lokalt eller test — forskjellen er bare verdiene du konstruerer den med.

```kotlin
// På Nais: leser KAFKA_BROKERS og SSL-oppsettet fra miljøvariablene Aiven setter.
val kafkaConfig = KafkaConfig.fraNaisEnv()

// Lokalt og i tester: ingen sikkerhet, bare en brokeradresse.
val kafkaConfig = KafkaConfig(kafkaBrokers = "localhost:9092")
```

Konsumentene velger typisk config etter kjøremiljø der de wires opp:

```kotlin
val kafkaConfig = if (Configuration.isNais()) KafkaConfig.fraNaisEnv(autoOffsetReset = "latest") else KafkaConfig(kafkaBrokers = "localhost:9092")
```

Spesialtilfeller overstyres ved å plusse på resultatmappa, siden siste verdi vinner ved like nøkler:

```kotlin
val config = kafkaConfig.consumerConfig(
    keyDeserializer = StringDeserializer(),
    valueDeserializer = StringDeserializer(),
    groupId = groupId,
) + mapOf(ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100)
```

## Sette opp en ny consumer

Lag en klasse som implementerer `Consumer<K, V>` og delegerer til en `ManagedKafkaConsumer`:

```kotlin
class MinConsumer(
    topic: String,
    groupId: String,
    kafkaConfig: KafkaConfig,
) : Consumer<String, String> {
    private val consumer = ManagedKafkaConsumer(
        topic = topic,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = StringDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = groupId,
        ),
        consume = ::consume,
    )

    override suspend fun consume(key: String, value: String) {
        // Håndter meldingen.
    }

    override fun run() = consumer.run()
}
```

Konsumenter startes kun på Nais, siden det verken finnes Kafka eller schema registry i det lokale oppsettet:

```kotlin
if (Configuration.isNais()) {
    applicationContext.minConsumer.run()
}
```

## Producer

`Producer` tar konfigurasjonsmappa direkte, på samme måte som `ManagedKafkaConsumer`:

```kotlin
val producer = Producer<String, String>(producerConfig = kafkaConfig.producerConfig())
```

`producerConfig()` bruker String-serialisering for nøkkel og verdi; trenger du noe annet, finnes en variant som tar serialisererne som parametre.

## Migrering fra det gamle API-et

Det gamle `KafkaConfig`-interfacet med `KafkaConfigImpl` og `LocalKafkaConfig` i pakka `kafka.config` er fjernet.

- `KafkaConfigImpl(autoOffsetReset = …)` → `KafkaConfig.fraNaisEnv(autoOffsetReset = …)`.
- `LocalKafkaConfig()` → `KafkaConfig(kafkaBrokers = "localhost:9092")`.
- `kafkaConfig.avroConsumerConfig(…)` → pakk configen i `AvroKafkaConfig` fra modulen `kafka-avro`.
- `Producer(kafkaConfig)` → `Producer(producerConfig = kafkaConfig.producerConfig())`.
- Pakkenavnet er nytt: `no.nav.tiltakspenger.libs.kafka.infra` (og `…kafka.avro.infra` for avro).

## Praktiske regler

- Produsentteamet må ofte gi appen lesetilgang til topicet, typisk via en PR i deres repo.
- Sørg for at topicet ligger i miljøkonfigurasjonen; følg appens konvensjon.
- `autoOffsetReset` bør settes til `"latest"` første gang en ny consumer group kjører, siden gruppa må initialisere en offset.
