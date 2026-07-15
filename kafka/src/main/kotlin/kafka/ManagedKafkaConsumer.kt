package no.nav.tiltakspenger.libs.kafka

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.tiltakspenger.libs.kafka.ManagedKafkaConsumer.Companion.BACKOFF_STEP_MILLIS
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class ManagedKafkaConsumer<K, V>(
    private val topic: String,
    private val config: Map<String, *>,
    private val pollDuration: Duration = Duration.ofSeconds(1),
    /** Maks tid [stop] venter på at en pågående batch blir ferdig behandlet og committet før coroutinen avbrytes. */
    private val shutdownTimeout: Duration = Duration.ofSeconds(10),
    private val kanLoggeKey: Boolean = true,
    baseBackoffDelayMillis: Long = 500L,
    initialBackoffDelayMillis: Long = 1000L,
    private val log: KLogger? = KotlinLogging.logger {},
    private val onRecordsPolled: (Int) -> Unit = {},
    private val consume: suspend (key: K, value: V) -> Unit,
) {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Volatile
    private var running = false

    private val started = AtomicBoolean(false)

    /** Job-en til den kjørende konsument-loopen, slik at [stop] kan vente på at den blir ferdig. */
    @Volatile
    private var consumerJob: Job? = null

    /**
     * Referanse til den kjørende consumeren slik at [stop] kan kalle [KafkaConsumer.wakeup] for å
     * avbryte en blokkerende poll/commit. wakeup() er den eneste trådsikre metoden på KafkaConsumer.
     */
    @Volatile
    private var consumer: KafkaConsumer<K, V>? = null

    val status: ConsumerStatus = ConsumerStatus(
        baseDelayMillis = baseBackoffDelayMillis,
        initialDelayMillis = initialBackoffDelayMillis,
    )

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread { stop() },
        )
    }

    fun run(): Job {
        if (!started.compareAndSet(false, true)) {
            log?.error { "Consumer for topic $topic er allerede startet; ignorerer nytt kall til run()." }
            return consumerJob ?: job
        }
        // Sett running=true _før_ vi starter coroutinen. Ellers er det en race der stop() kan sette
        // running=false før coroutinen rekker å sette den til true, slik at consumeren kjører videre.
        running = true

        return scope.launch {
            log?.info { "Starting consumer for topic: $topic" }
            try {
                KafkaConsumer<K, V>(config).use { kafkaConsumer ->
                    consumer = kafkaConsumer
                    try {
                        consumeLoop(kafkaConsumer)
                    } finally {
                        consumer = null
                    }
                }
            } finally {
                running = false
            }
        }.also { consumerJob = it }
    }

    fun start() = run()

    fun stop() {
        log?.info { "Stopping consumer for topic: $topic" }
        running = false
        // Avbryt en eventuell blokkerende poll/commit slik at consumeren avslutter raskt,
        // i stedet for å vente ut pollDuration eller en pågående backoff.
        try {
            consumer?.wakeup()
        } catch (t: Throwable) {
            log?.warn(t) { "Klarte ikke å vekke consumer for topic $topic ved stopp" }
        }
        // Vent på at konsument-loopen fullfører pågående prosessering + commit, slik at vi ikke
        // avbryter midt i en batch. Da unngår vi unødvendig reprosessering (at-least-once) ved oppstart.
        val activeJob = consumerJob ?: return
        runBlocking {
            try {
                withTimeout(shutdownTimeout.toMillis().milliseconds) {
                    activeJob.join()
                }
            } catch (_: TimeoutCancellationException) {
                log?.warn {
                    "Consumer for topic $topic stoppet ikke innen $shutdownTimeout, avbryter coroutinen."
                }
                activeJob.cancel()
            }
        }
    }

    private suspend fun consumeLoop(consumer: KafkaConsumer<K, V>) {
        try {
            consumer.subscribe(listOf(topic))
            while (running) {
                // Én enkelt backoff per feil (ikke dobbel). Backoffen er avbruddsbar slik at stop()
                // ikke blokkerer i opptil flere minutter.
                if (status.isFailure) {
                    log?.info {
                        "Consumer status for topic $topic is failure, " +
                            "delaying ${status.backoffDuration}ms before retrying"
                    }
                    backoff()
                    if (!running) break
                }
                pollAndProcess(consumer)
            }
        } catch (_: WakeupException) {
            log?.info { "Consumer for $topic is exiting" }
        } catch (t: Throwable) {
            log?.error(t) { "Something went wrong with consumer for topic $topic" }
        } finally {
            running = false
        }
    }

    private suspend fun pollAndProcess(consumer: KafkaConsumer<K, V>) {
        val records = try {
            consumer.poll(pollDuration)
        } catch (e: WakeupException) {
            throw e
        } catch (t: Throwable) {
            // Feil ved selve pollingen (typisk broker-/tilkoblingsfeil). Vi forblir abonnert og lar
            // backoffen styre forsinkelsen før neste forsøk.
            log?.error(t) { t.message }
            status.failure()
            return
        }

        onRecordsPolled(records.count())

        if (records.isEmpty) {
            // En vellykket (tom) poll betyr at ev. tidligere feil er over -> nullstill backoff.
            status.success()
            return
        }

        log?.debug { "Consumer for $topic polled ${records.count()} records." }

        try {
            records.forEach { record -> process(record) }
            // det er viktig at committing av offset først skjer når alle records er behandlet ok, hvis ikke
            // risikerer vi at records som har feilet ikke blir forsøkt på nytt hvis vi har lest flere
            // records i en poll
            consumer.commitSync()
            status.success()
        } catch (e: WakeupException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            log?.error(t) { t.message }
            status.failure()
            // Spol tilbake til starten av batchen slik at de samme recordene leses på nytt, uten å
            // måtte unsubscribe og utløse en full (kostbar) rebalance.
            seekToBatchStart(consumer, records)
        }
    }

    /**
     * Avbruddsbar backoff: deler ventetiden opp i korte intervaller slik at [stop] (running=false)
     * fører til at vi avslutter innen [BACKOFF_STEP_MILLIS] i stedet for å vente ut hele backoff-perioden
     * (som kan være opptil flere minutter).
     */
    private suspend fun backoff() {
        var remaining = status.backoffDuration
        while (remaining > 0 && running) {
            val step = min(BACKOFF_STEP_MILLIS, remaining)
            delay(step.milliseconds)
            remaining -= step
        }
    }

    private fun seekToBatchStart(consumer: KafkaConsumer<K, V>, records: ConsumerRecords<K, V>) {
        records.partitions().forEach { partition ->
            val firstOffset = records.records(partition).firstOrNull()?.offset()
            if (firstOffset != null) {
                consumer.seek(partition, firstOffset)
            }
        }
    }

    private suspend fun process(record: ConsumerRecord<K, V>) {
        // noen topics bruker fnr som key, og da skal ikke disse logges til vanlig logg
        val keyLogStatement = if (kanLoggeKey) {
            "key=${record.key()} "
        } else {
            ""
        }
        try {
            consume(record.key(), record.value())
            log?.debug {
                "Consumed record for " +
                    "topic=${record.topic()} " +
                    keyLogStatement +
                    "partition=${record.partition()} " +
                    "offset=${record.offset()}"
            }
        } catch (t: Throwable) {
            val msg = "Failed to consume record for " +
                "topic=${record.topic()} " +
                keyLogStatement +
                "partition=${record.partition()} " +
                "offset=${record.offset()}"
            throw ConsumerProcessingException(msg, t)
        }
    }

    class ConsumerProcessingException(
        msg: String,
        cause: Throwable?,
    ) : RuntimeException(msg, cause)

    private companion object {
        const val BACKOFF_STEP_MILLIS = 200L
    }
}
