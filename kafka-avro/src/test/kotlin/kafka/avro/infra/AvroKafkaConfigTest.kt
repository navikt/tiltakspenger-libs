package no.nav.tiltakspenger.libs.kafka.avro.infra

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.tiltakspenger.libs.kafka.infra.KafkaConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test

class AvroKafkaConfigTest {

    private val naisEnv = mapOf(
        "KAFKA_BROKERS" to "broker.aiven:26484",
        "KAFKA_TRUSTSTORE_PATH" to "/var/run/secrets/truststore.jks",
        "KAFKA_KEYSTORE_PATH" to "/var/run/secrets/keystore.p12",
        "KAFKA_CREDSTORE_PASSWORD" to "hemmelig",
        "KAFKA_SCHEMA_REGISTRY" to "https://registry.aiven:26487",
        "KAFKA_SCHEMA_REGISTRY_USER" to "bruker",
        "KAFKA_SCHEMA_REGISTRY_PASSWORD" to "registrypassord",
    )

    private val lokalKafkaConfig = KafkaConfig(kafkaBrokers = "localhost:9092")

    private fun AvroKafkaConfig.avro(useSpecificAvroReader: Boolean = true) = avroConsumerConfig(
        keyDeserializer = StringDeserializer(),
        valueDeserializer = StringDeserializer(),
        groupId = "test-gruppe",
        useSpecificAvroReader = useSpecificAvroReader,
    )

    @Test
    fun `avroConsumerConfig - setter ingen basic-auth når basicAuth ikke er satt`() {
        // Lokalt og i tester kjører schema registry uten auth.
        // USER_INFO uten tilhørende basic.auth.user.info gjør at avro-deserialisereren ikke kan konstrueres.
        val config = AvroKafkaConfig(lokalKafkaConfig, schemaRegistryUrl = "mock://test").avro()

        config shouldNotContainKey "basic.auth.credentials.source"
        config shouldNotContainKey "basic.auth.user.info"
    }

    @Test
    fun `avroConsumerConfig - propagerer schema registry-url og avro-reader-flagget`() {
        val config = AvroKafkaConfig(lokalKafkaConfig, schemaRegistryUrl = "mock://test").avro()

        config["schema.registry.url"] shouldBe "mock://test"
        config["specific.avro.reader"] shouldBe true
    }

    @Test
    fun `avroConsumerConfig - useSpecificAvroReader er true som standard`() {
        val config = AvroKafkaConfig(lokalKafkaConfig, schemaRegistryUrl = "mock://test").avroConsumerConfig(
            keyDeserializer = StringDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = "test-gruppe",
        )

        config["specific.avro.reader"] shouldBe true
    }

    @Test
    fun `avroConsumerConfig - useSpecificAvroReader false gir GenericRecord-lesing`() {
        val config = AvroKafkaConfig(lokalKafkaConfig, schemaRegistryUrl = "mock://test").avro(useSpecificAvroReader = false)

        config["specific.avro.reader"] shouldBe false
    }

    @Test
    fun `avroConsumerConfig - delegerer consumer-configen til den innpakkede KafkaConfigen`() {
        val config = AvroKafkaConfig(lokalKafkaConfig, schemaRegistryUrl = "mock://test").avro()

        config[ConsumerConfig.GROUP_ID_CONFIG] shouldBe "test-gruppe"
        config[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] shouldBe "localhost:9092"
    }

    @Test
    fun `avroConsumerConfig - setter basic-auth når basicAuth er satt`() {
        val config = AvroKafkaConfig(
            kafkaConfig = lokalKafkaConfig,
            schemaRegistryUrl = "https://registry.aiven:26487",
            basicAuth = SchemaRegistryBasicAuth(brukernavn = "bruker", passord = "registrypassord"),
        ).avro()

        config["basic.auth.credentials.source"] shouldBe "USER_INFO"
        config["basic.auth.user.info"] shouldBe "bruker:registrypassord"
    }

    @Test
    fun `fraNaisEnv - leser broker, schema registry og basic-auth fra miljøet`() {
        val avroKafkaConfig = AvroKafkaConfig.fraNaisEnv(autoOffsetReset = "latest", env = naisEnv)
        val config = avroKafkaConfig.avro()

        config["schema.registry.url"] shouldBe "https://registry.aiven:26487"
        config["basic.auth.user.info"] shouldBe "bruker:registrypassord"
        config[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] shouldBe "latest"
        // Den innpakkede KafkaConfigen er tilgjengelig for konsumenter som også har vanlige topics.
        avroKafkaConfig.kafkaConfig.commonConfig()[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] shouldBe "broker.aiven:26484"
    }

    @Test
    fun `fraNaisEnv - feiler med navnet på miljøvariabelen som mangler`() {
        val feil = shouldThrow<IllegalStateException> {
            AvroKafkaConfig.fraNaisEnv(env = naisEnv - "KAFKA_SCHEMA_REGISTRY")
        }

        feil.message shouldContain "KAFKA_SCHEMA_REGISTRY"
    }

    @Test
    fun `fraNaisEnv - leser prosessens eget miljø som standard`() {
        // Testen dekker default-parameteren System.getenv() og forutsetter at kjøremiljøet mangler minst én av variablene.
        check(naisEnv.keys.any { System.getenv(it) == null }) {
            "Kjøremiljøet har hele Kafka-oppsettet i miljøvariablene; da kan ikke denne testen verifisere feilmeldingen for manglende variabel."
        }
        val feil = shouldThrow<IllegalStateException> {
            AvroKafkaConfig.fraNaisEnv()
        }

        feil.message shouldContain "Mangler påkrevd miljøvariabel"
    }

    @Test
    fun `SchemaRegistryBasicAuth - toString maskerer passordet`() {
        val basicAuth = SchemaRegistryBasicAuth(brukernavn = "bruker", passord = "registrypassord")

        basicAuth.toString() shouldNotContain "registrypassord"
        basicAuth.toString() shouldContain "bruker"
    }
}
