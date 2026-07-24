package no.nav.tiltakspenger.libs.kafka.config

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test

class LocalKafkaConfigTest {

    private fun LocalKafkaConfig.avro() = avroConsumerConfig(
        keyDeserializer = StringDeserializer(),
        valueDeserializer = StringDeserializer(),
        groupId = "test",
        useSpecificAvroReader = true,
    )

    @Test
    fun `avroConsumerConfig - setter ingen basic-auth mot lokalt schema registry`() {
        // Lokalt schema registry kjører uten auth.
        // USER_INFO uten basic.auth.user.info gjør at avro-deserialisereren ikke kan konstrueres, jf. ManagedKafkaConsumerTest sin fake.
        val config = LocalKafkaConfig().avro()

        config shouldNotContainKey "basic.auth.credentials.source"
        config shouldNotContainKey "basic.auth.user.info"
    }

    @Test
    fun `avroConsumerConfig - propagerer eksplisitt schema registry-url`() {
        val config = LocalKafkaConfig(avroSchemaRegistry = "mock://test").avro()

        config["schema.registry.url"] shouldBe "mock://test"
    }

    @Test
    fun `avroConsumerConfig - arver consumer-config og avro-reader-flagget`() {
        val config = LocalKafkaConfig().avro()

        config shouldContainKey ConsumerConfig.GROUP_ID_CONFIG
        config["specific.avro.reader"] shouldBe true
    }

    @Test
    fun `KafkaConfigImpl - setter fortsatt basic-auth mot autentisert schema registry`() {
        // Kontrast til LocalKafkaConfig: den ordinære konfigen skal beholde basic-auth-oppsettet.
        val config = KafkaConfigImpl().avroConsumerConfig(
            keyDeserializer = StringDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = "test",
            useSpecificAvroReader = true,
        )

        config shouldContainKey "basic.auth.credentials.source"
        config shouldContainKey "basic.auth.user.info"
    }
}
