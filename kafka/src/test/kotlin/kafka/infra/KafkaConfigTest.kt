package no.nav.tiltakspenger.libs.kafka.infra

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test

class KafkaConfigTest {

    private val naisEnv = mapOf(
        "KAFKA_BROKERS" to "broker.aiven:26484",
        "KAFKA_TRUSTSTORE_PATH" to "/var/run/secrets/truststore.jks",
        "KAFKA_KEYSTORE_PATH" to "/var/run/secrets/keystore.p12",
        "KAFKA_CREDSTORE_PASSWORD" to "hemmelig",
    )

    private fun KafkaConfig.consumer() = consumerConfig(
        keyDeserializer = StringDeserializer(),
        valueDeserializer = StringDeserializer(),
        groupId = "test-gruppe",
    )

    @Test
    fun `commonConfig - inneholder broker og ingen sikkerhet som standard`() {
        val config = KafkaConfig(kafkaBrokers = "localhost:9092").commonConfig()

        config[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] shouldBe "localhost:9092"
        config shouldNotContainKey CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
    }

    @Test
    fun `commonConfig - ssl-sikkerhet legger på hele oppsettet`() {
        val config = KafkaConfig(
            kafkaBrokers = "broker.aiven:26484",
            sikkerhet = KafkaSikkerhet.Ssl(
                truststorePath = "/sti/truststore.jks",
                keystorePath = "/sti/keystore.p12",
                credstorePassword = "hemmelig",
            ),
        ).commonConfig()

        config[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] shouldBe "SSL"
        config[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] shouldBe "/sti/truststore.jks"
        config[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] shouldBe "/sti/keystore.p12"
        config[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] shouldBe "hemmelig"
        config[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] shouldBe "hemmelig"
        config[SslConfigs.SSL_KEY_PASSWORD_CONFIG] shouldBe "hemmelig"
    }

    @Test
    fun `consumerConfig - setter gruppe, deserialiserere og faste verdier`() {
        val config = KafkaConfig(kafkaBrokers = "localhost:9092").consumer()

        config[ConsumerConfig.GROUP_ID_CONFIG] shouldBe "test-gruppe"
        config[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] shouldBe StringDeserializer::class.java
        config[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] shouldBe StringDeserializer::class.java
        config[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] shouldBe "earliest"
        config[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] shouldBe false
        config[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe 1
        config[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] shouldBe "localhost:9092"
    }

    @Test
    fun `consumerConfig - autoOffsetReset kan overstyres i konstruktøren`() {
        val config = KafkaConfig(kafkaBrokers = "localhost:9092", autoOffsetReset = "latest").consumer()

        config[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] shouldBe "latest"
    }

    @Test
    fun `consumerConfig - spesialtilfeller overstyres ved å plusse på mappa`() {
        val config = KafkaConfig(kafkaBrokers = "localhost:9092").consumer() +
            mapOf(ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100)

        config[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe 100
    }

    @Test
    fun `producerConfig - string-serialisering og idempotens som standard`() {
        val config = KafkaConfig(kafkaBrokers = "localhost:9092").producerConfig()

        config[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] shouldBe StringSerializer::class.java
        config[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] shouldBe StringSerializer::class.java
        config[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] shouldBe true
        config[ProducerConfig.ACKS_CONFIG] shouldBe "all"
        config[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] shouldBe "localhost:9092"
    }

    @Test
    fun `producerConfig - serialiserere kan overstyres med den typede varianten`() {
        val config = KafkaConfig(kafkaBrokers = "localhost:9092").producerConfig(
            keySerializer = StringSerializer(),
            valueSerializer = ByteArraySerializer(),
        )

        config[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] shouldBe ByteArraySerializer::class.java
    }

    @Test
    fun `fraNaisEnv - leser broker og ssl-oppsett fra miljøet`() {
        val config = KafkaConfig.fraNaisEnv(autoOffsetReset = "latest", env = naisEnv).consumer()

        config[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] shouldBe "broker.aiven:26484"
        config[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] shouldBe "SSL"
        config[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] shouldBe "/var/run/secrets/truststore.jks"
        config[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] shouldBe "/var/run/secrets/keystore.p12"
        config[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] shouldBe "latest"
    }

    @Test
    fun `fraNaisEnv - feiler med navnet på miljøvariabelen som mangler`() {
        val feil = shouldThrow<IllegalStateException> {
            KafkaConfig.fraNaisEnv(env = naisEnv - "KAFKA_TRUSTSTORE_PATH")
        }

        feil.message shouldContain "KAFKA_TRUSTSTORE_PATH"
    }

    @Test
    fun `Ssl - toString maskerer passordet`() {
        val ssl = KafkaSikkerhet.Ssl(
            truststorePath = "/sti/truststore.jks",
            keystorePath = "/sti/keystore.p12",
            credstorePassword = "hemmelig",
        )

        ssl.toString() shouldNotContain "hemmelig"
        ssl.toString() shouldContain "/sti/truststore.jks"
    }
}
