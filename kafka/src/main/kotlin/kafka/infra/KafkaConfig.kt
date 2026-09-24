package no.nav.tiltakspenger.libs.kafka.infra

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

const val MAX_POLL_INTERVAL_MS = 300_000
const val MAX_POLL_RECORDS = 1

/**
 * Bygger konfigurasjonsmappene Kafka-klientene trenger, uavhengig av kjøremiljø.
 * Klassen er ren data uten miljølesing: verdiene kommer inn via konstruktøren, og [fraNaisEnv] er fabrikken som leser Nais-miljøet.
 * Lokalt og i tester konstrueres den direkte, f.eks. `KafkaConfig(kafkaBrokers = "localhost:9092")`.
 * Spesialtilfeller overstyres ved å plusse på resultatmappa, siden siste verdi vinner ved like nøkler.
 * Avro-konsumenter pakker den inn i `AvroKafkaConfig` fra modulen `kafka-avro`.
 */
class KafkaConfig(
    private val kafkaBrokers: String,
    private val autoOffsetReset: String = "earliest",
    private val sikkerhet: KafkaSikkerhet = KafkaSikkerhet.Ingen,
) {
    fun commonConfig(): Map<String, Any> = mapOf(
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
    ) + sikkerhet.tilConfig()

    fun <K, V> consumerConfig(
        keyDeserializer: Deserializer<K>,
        valueDeserializer: Deserializer<V>,
        groupId: String,
    ): Map<String, Any> = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to keyDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to valueDeserializer::class.java,
        ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to MAX_POLL_INTERVAL_MS,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to MAX_POLL_RECORDS,
    ) + commonConfig()

    /** Produsent-config med String-serialisering for både nøkkel og verdi, som er standardvalget i appene våre. */
    fun producerConfig(): Map<String, Any> = producerConfig(
        keySerializer = StringSerializer(),
        valueSerializer = StringSerializer(),
    )

    fun <K, V> producerConfig(
        keySerializer: Serializer<K>,
        valueSerializer: Serializer<V>,
    ): Map<String, Any> = mapOf(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to keySerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to valueSerializer::class.java,
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.RETRIES_CONFIG to Int.MAX_VALUE,
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
    ) + commonConfig()

    companion object {
        /**
         * Config for apper som kjører på Nais, der Aiven-oppsettet ligger i miljøvariabler.
         * Miljøet kan injiseres for test; utenom det leses prosessens eget miljø.
         */
        fun fraNaisEnv(
            autoOffsetReset: String = "earliest",
            env: Map<String, String> = System.getenv(),
        ): KafkaConfig = KafkaConfig(
            kafkaBrokers = env.påkrevd("KAFKA_BROKERS"),
            autoOffsetReset = autoOffsetReset,
            sikkerhet = KafkaSikkerhet.Ssl(
                truststorePath = env.påkrevd("KAFKA_TRUSTSTORE_PATH"),
                keystorePath = env.påkrevd("KAFKA_KEYSTORE_PATH"),
                credstorePassword = env.påkrevd("KAFKA_CREDSTORE_PASSWORD"),
            ),
        )
    }
}

/** Feiler med variabelnavnet i meldingen, i stedet for å la en tom verdi gi kryptiske tilkoblingsfeil senere. */
private fun Map<String, String>.påkrevd(navn: String): String = this[navn]
    ?: error("Mangler påkrevd miljøvariabel $navn for Kafka-oppsettet på Nais.")
