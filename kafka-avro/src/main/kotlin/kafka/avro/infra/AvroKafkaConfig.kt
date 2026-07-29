package no.nav.tiltakspenger.libs.kafka.avro.infra

import no.nav.tiltakspenger.libs.kafka.infra.KafkaConfig
import org.apache.kafka.common.serialization.Deserializer

/**
 * Legger schema registry- og avro-oppsett oppå en [KafkaConfig] for konsumenter som leser avro-topics.
 * Komposisjon framfor arv: klassen pakker inn en vilkårlig KafkaConfig, så nais-, lokal- og testvariantene trenger ingen egne avro-klasser.
 * Autentiseringen mot schema registry er data: sett [basicAuth] på Nais, og la den stå tom lokalt og i tester, der registry kjører uten auth.
 * Modulen har med vilje ingen avhengighet til `io.confluent:kafka-avro-serializer`; propertynavnene er hardkodet, og konsumenten eier selv deserialisererne og den sårbarhetsutsatte avhengigheten.
 */
class AvroKafkaConfig(
    val kafkaConfig: KafkaConfig,
    private val schemaRegistryUrl: String,
    private val basicAuth: SchemaRegistryBasicAuth? = null,
) {
    /**
     * Consumer-config for avro-topics, bygget på [KafkaConfig.consumerConfig].
     * useSpecificAvroReader: sett false for å tolke alt som GenericRecord i stedet for genererte skjemaklasser.
     */
    fun <K, V> avroConsumerConfig(
        keyDeserializer: Deserializer<K>,
        valueDeserializer: Deserializer<V>,
        groupId: String,
        useSpecificAvroReader: Boolean = true,
    ): Map<String, Any> = kafkaConfig.consumerConfig(
        keyDeserializer = keyDeserializer,
        valueDeserializer = valueDeserializer,
        groupId = groupId,
    ) + mapOf(
        "schema.registry.url" to schemaRegistryUrl,
        "specific.avro.reader" to useSpecificAvroReader,
    ) + (basicAuth?.tilConfig() ?: emptyMap())

    companion object {
        /**
         * Avro-config for apper som kjører på Nais, der både broker- og schema registry-oppsettet ligger i miljøvariabler.
         * Miljøet kan injiseres for test; utenom det leses prosessens eget miljø.
         */
        fun fraNaisEnv(
            autoOffsetReset: String = "earliest",
            env: Map<String, String> = System.getenv(),
        ): AvroKafkaConfig = AvroKafkaConfig(
            kafkaConfig = KafkaConfig.fraNaisEnv(autoOffsetReset = autoOffsetReset, env = env),
            schemaRegistryUrl = env.påkrevd("KAFKA_SCHEMA_REGISTRY"),
            basicAuth = SchemaRegistryBasicAuth(
                brukernavn = env.påkrevd("KAFKA_SCHEMA_REGISTRY_USER"),
                passord = env.påkrevd("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
            ),
        )
    }
}

/** Feiler med variabelnavnet i meldingen, i stedet for å la en tom verdi gi kryptiske tilkoblingsfeil senere. */
private fun Map<String, String>.påkrevd(navn: String): String = this[navn]
    ?: error("Mangler påkrevd miljøvariabel $navn for Kafka-oppsettet på Nais.")
