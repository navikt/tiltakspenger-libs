package no.nav.tiltakspenger.libs.kafka

import mu.KotlinLogging
import no.nav.tiltakspenger.libs.kafka.config.KafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class Producer<K, V>(
    kafkaConfig: KafkaConfig,
    gracePeriodMillis: Long = 1000,
) {
    private val log = KotlinLogging.logger {}
    private val producer = KafkaProducer<K, V>(kafkaConfig.producerConfig())

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info { "Shutting down Kafka producer in $gracePeriodMillis milliseconds..." }
                Thread.sleep(Duration.ofMillis(gracePeriodMillis))
                producer.close()
            },
        )
    }

    fun produce(
        topic: String,
        key: K,
        value: V,
    ) {
        val record = ProducerRecord(
            topic,
            key,
            value,
        )

        val metadata = producer.send(record).get()

        log.info {
            "Produserte melding til topic ${metadata.topic()}, " +
                "key=$key, " +
                "offset=${metadata.offset()}, " +
                "partition=${metadata.partition()}"
        }
    }

    fun tombstone(topic: String, key: K) {
        val value: V? = null
        val record = ProducerRecord(
            topic,
            key,
            value,
        )

        val metadata = producer.send(record).get()

        log.info {
            "Produserte tombstone til topic ${metadata.topic()}, " +
                "key=$key, " +
                "offset=${metadata.offset()}, " +
                "partition=${metadata.partition()}"
        }
    }
}
