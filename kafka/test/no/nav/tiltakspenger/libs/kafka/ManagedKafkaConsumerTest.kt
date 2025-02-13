package no.nav.tiltakspenger.libs.kafka

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.kafka.config.LocalKafkaConfig
import no.nav.tiltakspenger.libs.kafka.test.SingletonKafkaProvider
import no.nav.tiltakspenger.libs.kafka.test.eventually
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.IntegerDeserializer
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.apache.kafka.common.serialization.UUIDSerializer
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Properties
import java.util.UUID

class ManagedKafkaConsumerTest {
    private val topic = "test.topic"

    private val stringConsumerConfig = LocalKafkaConfig(SingletonKafkaProvider.getHost())
        .consumerConfig(
            keyDeserializer = StringDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = "test-consumer-${UUID.randomUUID()}",
        )

    private val intConsumerConfig = LocalKafkaConfig(SingletonKafkaProvider.getHost())
        .consumerConfig(
            keyDeserializer = IntegerDeserializer(),
            valueDeserializer = IntegerDeserializer(),
            groupId = "test-consumer-${UUID.randomUUID()}",
        )

    @Test
    fun `ManagedKafkaConsumer - konsumerer record med String, String`() {
        val key = "key"
        val value = "value"
        val cache = mutableMapOf<String, String>()

        produceStringString(ProducerRecord(topic, key, value))

        val consumer = ManagedKafkaConsumer(topic, stringConsumerConfig) { k: String, v: String ->
            cache[k] = v
        }
        consumer.run()

        eventually {
            cache[key] shouldBe value
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumerer record med UUID, ByteArray`() {
        val key = UUID.randomUUID()
        val value = "value".toByteArray()
        val cache = mutableMapOf<UUID, ByteArray>()
        val uuidTopic = "uuid.topic"

        produceUUIDByteArray(ProducerRecord(uuidTopic, key, value))

        val config = LocalKafkaConfig(SingletonKafkaProvider.getHost())
            .consumerConfig(
                keyDeserializer = UUIDDeserializer(),
                valueDeserializer = ByteArrayDeserializer(),
                groupId = "test-consumer-${UUID.randomUUID()}",
            )

        val consumer = ManagedKafkaConsumer(uuidTopic, config) { k: UUID, v: ByteArray ->
            cache[k] = v
        }
        consumer.run()

        eventually {
            cache[key] shouldBe value
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - prøver å konsumere melding på nytt hvis noe feiler`() {
        val key = "key"
        val value = "value"

        var antallGangerKallt = 0

        produceStringString(ProducerRecord(topic, key, value))

        val consumer = ManagedKafkaConsumer<String, String>(topic, stringConsumerConfig) { _, _ ->
            antallGangerKallt++
            error("skal feile noen ganger")
        }
        consumer.run()

        eventually {
            antallGangerKallt shouldBe 2
            consumer.status.retries shouldBe antallGangerKallt
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumerer mange meldinger med feil og flere partisjoner`() {
        val intTopic = NewTopic("int.topic-1", 4, 1)
        SingletonKafkaProvider.adminClient
            .createTopics(listOf(intTopic))
            .all()
            .get()

        val data = (1..100).toList()
        val consumed = mutableListOf<Int>()
        val failures = mutableListOf(7, 42, 42, 93)

        val consumer = ManagedKafkaConsumer<Int, Int>(intTopic.name(), intConsumerConfig) { k, _ ->
            if (k in failures) {
                failures.remove(k)
                error("Skal feile på $k")
            }
            consumed.add(k)
        }

        consumer.start()
        data.forEach {
            val partition = (0..3).random()
            produceIntInt(ProducerRecord(intTopic.name(), partition, it, it))
        }

        eventually(Duration.ofSeconds(15)) {
            consumed.size shouldBe data.size
            consumed.toSet().size shouldBe data.size
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumerer mange meldinger med samme key i riktig rekkefølge`() {
        val intTopic = NewTopic("int.topic-2", 4, 1)
        SingletonKafkaProvider.adminClient
            .createTopics(listOf(intTopic))
            .all()
            .get()

        val keys = (1..20).toList()
        val firstValue = 1
        val lastValue = 2
        val data = keys.map { Pair(it, firstValue) } + keys.map { Pair(it, lastValue) }

        val consumed = mutableMapOf<Int, Int>()
        val failures = mutableListOf(7, 42, 42)
        val consumer = ManagedKafkaConsumer<Int, Int>(intTopic.name(), intConsumerConfig) { k, v ->
            if (k in failures) {
                failures.remove(k)
                error("Skal feile på $k")
            }
            if (v == lastValue) {
                consumed[k] shouldBe firstValue
            }
            consumed[k] = v
        }

        consumer.start()

        data.forEach {
            val partition = it.first % intTopic.numPartitions()
            produceIntInt(ProducerRecord(intTopic.name(), partition, it.first, it.second))
        }

        eventually(Duration.ofSeconds(15)) {
            consumed.values.toSet() shouldBe setOf(lastValue)
            consumer.stop()
        }
    }
}

private fun produceIntInt(record: ProducerRecord<Int, Int>): RecordMetadata {
    KafkaProducer<Int, Int>(
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SingletonKafkaProvider.getHost())
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, IntegerSerializer::class.java)
        },
    ).use { producer ->
        return producer.send(record).get()
    }
}

private fun produceStringString(record: ProducerRecord<String, String>): RecordMetadata {
    KafkaProducer<String, String>(
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SingletonKafkaProvider.getHost())
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        },
    ).use { producer ->
        return producer.send(record).get()
    }
}

private fun produceUUIDByteArray(record: ProducerRecord<UUID, ByteArray>): RecordMetadata {
    KafkaProducer<UUID, ByteArray>(
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SingletonKafkaProvider.getHost())
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, UUIDSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
        },
    ).use { producer ->
        return producer.send(record).get()
    }
}
