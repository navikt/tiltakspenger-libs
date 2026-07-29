package no.nav.tiltakspenger.libs.kafka.infra

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

/**
 * Produsent som logger metadata for hver melding og lukker seg selv ved shutdown.
 * Konfigurasjonen bygges typisk med [KafkaConfig.producerConfig], eventuelt med overstyringer plusset på.
 */
class Producer<K, V>(
    producerConfig: Map<String, *>,
    gracePeriodMillis: Long = 1000,
    private val kanLoggeKey: Boolean = true,
) {
    private val log = KotlinLogging.logger {}
    private val producer = KafkaProducer<K, V>(producerConfig)

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
        // noen topics bruker fnr som key, og da skal ikke disse logges til vanlig logg
        val keyLogStatement = if (kanLoggeKey) {
            "key=${record.key()} "
        } else {
            ""
        }

        log.info {
            "Produserte melding til topic ${metadata.topic()}, " +
                keyLogStatement +
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
        val keyLogStatement = if (kanLoggeKey) {
            "key=${record.key()} "
        } else {
            ""
        }

        log.info {
            "Produserte tombstone til topic ${metadata.topic()}, " +
                keyLogStatement +
                "offset=${metadata.offset()}, " +
                "partition=${metadata.partition()}"
        }
    }
}
