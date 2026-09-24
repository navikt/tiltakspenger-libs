# kafka-avro

Avro-oppsett for Kafka-konsumenter, som et tillegg til modulen `kafka`.
Kun apper som faktisk leser avro-topics skal avhenge av denne modulen.

## Hvorfor egen modul

Avro drar med seg schema registry-oppsett og i konsumentene den sårbarhetsutsatte avhengigheten `io.confluent:kafka-avro-serializer` (som også drar inn Jackson 2).
Apper uten avro skal ikke se noe av dette, verken som API-flate eller på classpathen.
Denne modulen har derfor med vilje ingen confluent-avhengighet: propertynavnene er hardkodet, og appen eier selv `KafkaAvroDeserializer` og versjonen av confluent-biblioteket.

## Bruk

`AvroKafkaConfig` pakker inn en vilkårlig `KafkaConfig` (komposisjon, ikke arv) og legger på schema registry-oppsettet.

På Nais leses alt fra miljøvariablene Aiven setter:

```kotlin
val avroKafkaConfig = AvroKafkaConfig.fraNaisEnv(autoOffsetReset = "latest")

val consumer = ManagedKafkaConsumer(
    topic = topic,
    config = avroKafkaConfig.avroConsumerConfig(
        keyDeserializer = StringDeserializer(),
        valueDeserializer = KafkaAvroDeserializer(),
        groupId = groupId,
    ),
    consume = ::consume,
)
```

Lokalt og i tester finnes ingen autentisering, så `basicAuth` utelates:

```kotlin
val avroKafkaConfig = AvroKafkaConfig(
    kafkaConfig = KafkaConfig(kafkaBrokers = "localhost:9092"),
    schemaRegistryUrl = "mock://test",
)
```

Trenger appen både avro- og vanlige konsumenter, bygg én `KafkaConfig` og del den: den innpakkede configen er tilgjengelig som `avroKafkaConfig.kafkaConfig`.

`useSpecificAvroReader` er `true` som standard og bruker de genererte skjemaklassene; sett `false` for å lese alt som `GenericRecord`.
