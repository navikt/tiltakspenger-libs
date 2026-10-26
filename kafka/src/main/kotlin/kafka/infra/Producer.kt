package no.nav.tiltakspenger.libs.kafka.infra

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

/**
 * Produsent som logger metadata for hver melding.
 * Konfigurasjonen bygges typisk med [KafkaConfig.producerConfig], eventuelt med overstyringer plusset på.
 *
 * Produsenten lukker seg ikke selv, heller ikke ved shutdown.
 * Det er trygt å la den stå åpen til JVM-en avslutter, av to grunner.
 * Hver sending venter på kvittering fra Kafka, så det ligger aldri usendte meldinger i bufferen som en lukking ville tømt.
 * Nettverkstråden til [KafkaProducer] er en daemon-tråd, så den holder ikke JVM-en i live.
 * Den registrerer bevisst ingen shutdown-hook, fordi en slik hook kjører parallelt med avslutningen i Ktor og kan stenge produsenten mens jobbene fortsatt sender.
 * Vil konsumenten likevel lukke den, kaller den [close] etter at jobbene som sender, er stoppet.
 */
class Producer<K, V>(
    producerConfig: Map<String, *>,
    private val kanLoggeKey: Boolean = true,
) : AutoCloseable {
    private val log = KotlinLogging.logger {}
    private val producer = KafkaProducer<K, V>(producerConfig)

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

    override fun close() {
        producer.close()
    }
}
