package no.nav.tiltakspenger.libs.kafka.infra

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.kafka.test.SingletonKafkaProvider
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class BakgrunnsprosessmålingerTest {
    @Test
    fun `gjenbruker holderen ved gjentatt registrering`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val målinger = Bakgrunnsprosessmålinger(registry, TikkendeKlokke())
        målinger.registrer("topic", Duration.ofSeconds(1))
        val før = registry.sistVellykket("topic")

        målinger.registrer("topic", Duration.ofSeconds(1))
        målinger.registrerVellykketKjøring("topic")

        registry.sistVellykket("topic") shouldBeGreaterThan før
    }

    @Test
    fun `avviser samme prosessnavn fra en annen målingsinstans`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        Bakgrunnsprosessmålinger(registry, fixedClock).registrer("topic", Duration.ofSeconds(1))

        val feil = shouldThrow<IllegalStateException> {
            Bakgrunnsprosessmålinger(registry, fixedClock).registrer("topic", Duration.ofSeconds(1))
        }

        feil.message shouldContain "topic"
        feil.message shouldContain "unikt per register"
    }

    @Test
    fun `avviser vellykket kjøring for en prosess som ikke er registrert`() {
        val målinger = Bakgrunnsprosessmålinger(PrometheusMeterRegistry(PrometheusConfig.DEFAULT), fixedClock)

        val feil = shouldThrow<IllegalArgumentException> {
            målinger.registrerVellykketKjøring("ukjent")
        }

        feil.message shouldContain "ukjent"
    }

    @Test
    fun `eksponerer kontrakten ved oppstart og oppdaterer etter vellykket tom poll`() {
        val topic = "måling"
        val clock = TikkendeKlokke()
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val consumer = ManagedKafkaConsumer(
            topic = topic,
            config = KafkaConfig(kafkaBrokers = SingletonKafkaProvider.getHost()).consumerConfig(
                keyDeserializer = StringDeserializer(),
                valueDeserializer = StringDeserializer(),
                groupId = "måling-${UUID.randomUUID()}",
            ),
            pollDuration = Duration.ofMillis(100),
            log = null,
            clock = clock,
            meterRegistry = registry,
        ) { _: String, _: String -> }

        registry.sistVellykket(topic) shouldBe 0.0
        registry.intervall(topic) shouldBe 0.1
        registry.scrape() shouldContain
            """tpts_bakgrunnsprosess_sist_vellykket_tidspunkt_sekunder{prosess="$topic",type="meldingsleser"}"""
        registry.scrape() shouldContain
            """tpts_bakgrunnsprosess_intervall_sekunder{prosess="$topic",type="meldingsleser"}"""

        consumer.run()
        try {
            runBlocking {
                eventually(10.seconds) {
                    registry.sistVellykket(topic) shouldBeGreaterThan 0.0
                }
            }
        } finally {
            consumer.stop()
        }
    }

    private fun PrometheusMeterRegistry.sistVellykket(prosess: String): Double =
        get("tpts.bakgrunnsprosess.sist_vellykket_tidspunkt_sekunder")
            .tags("prosess", prosess, "type", "meldingsleser")
            .gauge()
            .value()

    private fun PrometheusMeterRegistry.intervall(prosess: String): Double =
        get("tpts.bakgrunnsprosess.intervall_sekunder")
            .tags("prosess", prosess, "type", "meldingsleser")
            .gauge()
            .value()
}
