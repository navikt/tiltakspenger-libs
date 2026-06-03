package no.nav.tiltakspenger.libs.kafka

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.kafka.config.LocalKafkaConfig
import no.nav.tiltakspenger.libs.kafka.test.SingletonKafkaProvider
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
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
import kotlin.time.Duration.Companion.seconds

class ManagedKafkaConsumerTest {
    private val topic = "test.topic"

    /** Fast poll + backoff settings for tests to avoid unnecessary waiting. */
    private val testPollDuration = Duration.ofMillis(100)
    private val testBaseBackoffDelayMillis = 1L
    private val testInitialBackoffDelayMillis = 1L

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

        val consumer = ManagedKafkaConsumer(
            topic = topic,
            config = stringConsumerConfig,
            pollDuration = testPollDuration,
            baseBackoffDelayMillis = testBaseBackoffDelayMillis,
            initialBackoffDelayMillis = testInitialBackoffDelayMillis,
            log = null,
        ) { k: String, v: String ->
            cache[k] = v
        }
        consumer.run()

        try {
            runBlocking {
                eventually {
                    cache[key] shouldBe value
                }
            }
        } finally {
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

        val consumer = ManagedKafkaConsumer(
            topic = uuidTopic,
            config = config,
            pollDuration = testPollDuration,
            baseBackoffDelayMillis = testBaseBackoffDelayMillis,
            initialBackoffDelayMillis = testInitialBackoffDelayMillis,
            log = null,
        ) { k: UUID, v: ByteArray ->
            cache[k] = v
        }
        consumer.run()

        try {
            runBlocking {
                eventually {
                    cache[key] shouldBe value
                }
            }
        } finally {
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - prøver å konsumere melding på nytt hvis noe feiler`() {
        val key = "key"
        val value = "value"
        var antallGangerKallt = 0

        produceStringString(ProducerRecord(topic, key, value))

        val consumer = ManagedKafkaConsumer<String, String>(
            topic = topic,
            config = stringConsumerConfig,
            pollDuration = testPollDuration,
            baseBackoffDelayMillis = testBaseBackoffDelayMillis,
            initialBackoffDelayMillis = testInitialBackoffDelayMillis,
            log = null,
        ) { _, _ ->
            antallGangerKallt++
            error("skal feile noen ganger")
        }
        consumer.run()

        try {
            runBlocking {
                eventually {
                    (antallGangerKallt >= 2) shouldBe true
                    consumer.status.retries shouldBe antallGangerKallt
                }
            }
        } finally {
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

        val data = (1..30).toList()
        val consumed = mutableListOf<Int>()
        val failures = mutableListOf(7, 22, 22)
        val consumer = ManagedKafkaConsumer<Int, Int>(
            topic = intTopic.name(),
            config = intConsumerConfig,
            pollDuration = testPollDuration,
            baseBackoffDelayMillis = testBaseBackoffDelayMillis,
            initialBackoffDelayMillis = testInitialBackoffDelayMillis,
            log = null,
        ) { k, _ ->
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

        try {
            runBlocking {
                eventually(10.seconds) {
                    // hvis vi leser flere meldinger av gangen vil antall konsumerte meldinger være større enn antall
                    // produserte meldinger. Så lenge MAX_POLL_RECORDS=1 i consumerconfig skal disse være like.
                    consumed.size shouldBe data.size
                    consumed.toSet().size shouldBe data.size
                }
            }
        } finally {
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

        val keys = (1..10).toList()
        val firstValue = 1
        val lastValue = 2
        val data = keys.map { Pair(it, firstValue) } + keys.map { Pair(it, lastValue) }
        val consumed = mutableMapOf<Int, Int>()
        val failures = mutableListOf(7)
        val consumer = ManagedKafkaConsumer<Int, Int>(
            topic = intTopic.name(),
            config = intConsumerConfig,
            pollDuration = testPollDuration,
            baseBackoffDelayMillis = testBaseBackoffDelayMillis,
            initialBackoffDelayMillis = testInitialBackoffDelayMillis,
            log = null,
        ) { k, v ->
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

        try {
            runBlocking {
                eventually(10.seconds) {
                    consumed.values.toSet() shouldBe setOf(lastValue)
                }
            }
        } finally {
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumerer flere meldinger per poll når MAX_POLL_RECORDS over 1`() {
        val intTopic = NewTopic("int.topic-3", 4, 1)
        SingletonKafkaProvider.adminClient
            .createTopics(listOf(intTopic))
            .all()
            .get()

        val maxPollRecords = 100
        val antallMeldinger = 500
        val data = (1..antallMeldinger).toList()
        val consumed = mutableListOf<Int>()
        val pollBatchSizes = mutableListOf<Int>()

        val configWithHigherMaxPoll = intConsumerConfig + mapOf(
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollRecords,
        )

        // produser meldingene før konsumenten starter, slik at de samles opp og en enkelt poll kan returnere flere
        data.forEach {
            val partition = (0..3).random()
            produceIntInt(ProducerRecord(intTopic.name(), partition, it, it))
        }

        val consumer = ManagedKafkaConsumer<Int, Int>(
            topic = intTopic.name(),
            config = configWithHigherMaxPoll,
            pollDuration = testPollDuration,
            baseBackoffDelayMillis = testBaseBackoffDelayMillis,
            initialBackoffDelayMillis = testInitialBackoffDelayMillis,
            log = null,
            onRecordsPolled = { count -> if (count > 0) pollBatchSizes.add(count) },
        ) { k, _ ->
            consumed.add(k)
        }

        consumer.start()

        try {
            runBlocking {
                eventually(10.seconds) {
                    consumed.size shouldBe data.size
                    consumed.toSet() shouldBe data.toSet()
                    // verifiser at minst én poll faktisk returnerte mer enn 1 record, og at vi aldri overskrider grensen
                    (pollBatchSizes.max() > 1) shouldBe true
                    (pollBatchSizes.max() <= maxPollRecords) shouldBe true
                }
            }
        } finally {
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - run er idempotent`() {
        val consumer = ManagedKafkaConsumer(
            topic = topic,
            config = stringConsumerConfig,
            pollDuration = testPollDuration,
            baseBackoffDelayMillis = testBaseBackoffDelayMillis,
            initialBackoffDelayMillis = testInitialBackoffDelayMillis,
            log = null,
        ) { _: String, _: String -> }

        try {
            val firstJob = consumer.run()
            // Gjentatte kall skal ikke starte en ny loop, men returnere den samme job-en.
            consumer.run() shouldBe firstJob
            consumer.start() shouldBe firstJob
        } finally {
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - stop venter på at pågående prosessering blir ferdig`() {
        val key = "key"
        val value = "value"
        val topic = "graceful.stop.topic"
        produceStringString(ProducerRecord(topic, key, value))

        val config = LocalKafkaConfig(SingletonKafkaProvider.getHost())
            .consumerConfig(
                keyDeserializer = StringDeserializer(),
                valueDeserializer = StringDeserializer(),
                groupId = "test-consumer-${UUID.randomUUID()}",
            )

        var processingStarted = false
        var processingFinished = false
        val consumer = ManagedKafkaConsumer(
            topic = topic,
            config = config,
            pollDuration = testPollDuration,
            baseBackoffDelayMillis = testBaseBackoffDelayMillis,
            initialBackoffDelayMillis = testInitialBackoffDelayMillis,
            log = null,
        ) { _: String, _: String ->
            processingStarted = true
            // Simuler litt arbeid som må få fullføre selv om stop() kalles underveis.
            delay(500)
            processingFinished = true
        }
        consumer.run()

        runBlocking {
            eventually {
                processingStarted shouldBe true
            }
        }
        // stop() skal blokkere til den pågående prosesseringen er ferdig.
        consumer.stop()
        processingFinished shouldBe true
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
