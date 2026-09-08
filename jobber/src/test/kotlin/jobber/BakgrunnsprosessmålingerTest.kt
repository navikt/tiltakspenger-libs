package no.nav.tiltakspenger.libs.jobber

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.common.fixedClock
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class BakgrunnsprosessmålingerTest {
    @Test
    fun `eksponerer navn labels oppstartstidspunkt og intervall og oppdaterer etter suksess`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val clock = TikkendeKlokke()
        val målinger = Bakgrunnsprosessmålinger(registry, clock)

        målinger.registrer("opprydding", 90.seconds)

        registry.sistVellykket("opprydding") shouldBe 0.0
        registry.intervall("opprydding") shouldBe 90.0
        registry.scrape() shouldContain
            """tpts_bakgrunnsprosess_sist_vellykket_tidspunkt_sekunder{prosess="opprydding",type="jobb"}"""
        registry.scrape() shouldContain
            """tpts_bakgrunnsprosess_intervall_sekunder{prosess="opprydding",type="jobb"}"""

        målinger.registrerVellykketKjøring("opprydding")

        registry.sistVellykket("opprydding") shouldBe (fixedClock.instant().epochSecond + 1).toDouble()
    }

    @Test
    fun `gjenbruker holderen ved gjentatt registrering`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val målinger = Bakgrunnsprosessmålinger(registry, TikkendeKlokke())
        målinger.registrer("opprydding", 1.seconds)
        val før = registry.sistVellykket("opprydding")

        målinger.registrer("opprydding", 1.seconds)
        målinger.registrerVellykketKjøring("opprydding")

        registry.sistVellykket("opprydding") shouldBeGreaterThan før
    }

    @Test
    fun `avviser samme prosessnavn fra en annen målingsinstans`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        Bakgrunnsprosessmålinger(registry, fixedClock).registrer("opprydding", 1.seconds)

        val feil = shouldThrow<IllegalStateException> {
            Bakgrunnsprosessmålinger(registry, fixedClock).registrer("opprydding", 1.seconds)
        }

        feil.message shouldContain "opprydding"
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

    /**
     * Målingen må overleve GC fordi registeret bare holder en svak referanse til verdiholderen.
     */
    @Test
    fun `målingen overlever GC`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val målinger = Bakgrunnsprosessmålinger(registry, fixedClock)
        målinger.registrer("opprydding", 1.seconds)

        System.gc()

        registry.sistVellykket("opprydding") shouldBe 0.0
        målinger.registrerVellykketKjøring("opprydding")
    }

    @Test
    fun `oppdaterer ikke tidspunktet når en task kaster`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val clock = TikkendeKlokke()
        val kjøringer = AtomicInteger()
        val executor = startJob(registry, clock) {
            kjøringer.incrementAndGet()
            error("forventet feil")
        }

        try {
            ventTilKjørt(kjøringer)
        } finally {
            executor.stop()
        }

        registry.sistVellykket("gruppe") shouldBe 0.0
    }

    @Test
    fun `oppdaterer ikke tidspunktet når en task melder Feilet`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val clock = TikkendeKlokke()
        val kjøringer = AtomicInteger()
        val executor = startJob(registry, clock) {
            kjøringer.incrementAndGet()
            TaskResultat.Feilet
        }

        try {
            ventTilKjørt(kjøringer)
        } finally {
            executor.stop()
        }

        registry.sistVellykket("gruppe") shouldBe 0.0
    }

    @Test
    fun `IngenArbeid teller som vellykket kjøring`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val clock = TikkendeKlokke()
        val executor = startJob(registry, clock) { TaskResultat.IngenArbeid }

        try {
            runBlocking {
                eventually(10.seconds) {
                    registry.sistVellykket("gruppe") shouldBeGreaterThan 0.0
                }
            }
        } finally {
            executor.stop()
        }
    }

    @Test
    fun `parallelle og serielle grupper måles uavhengig`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val clock = TikkendeKlokke()
        val seriellKjøringer = AtomicInteger()
        val parallellKjøringer = AtomicInteger()
        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            grupper = nonEmptyListOf(
                TaskGruppe(
                    navn = "seriell",
                    intervall = 1.days,
                    tasks = nonEmptyListOf({
                        seriellKjøringer.incrementAndGet()
                        TaskResultat.Feilet
                    }),
                    initialDelay = kotlin.time.Duration.ZERO,
                ),
                TaskGruppe(
                    navn = "parallell",
                    intervall = 1.days,
                    tasks = nonEmptyListOf({
                        parallellKjøringer.incrementAndGet()
                        TaskResultat.Ferdig
                    }),
                    kjøremodus = Kjøremodus.PARALLELT,
                    initialDelay = kotlin.time.Duration.ZERO,
                ),
            ),
            mdcCallIdKey = "test",
            clock = clock,
            meterRegistry = registry,
        )

        try {
            runBlocking {
                eventually(10.seconds) {
                    seriellKjøringer.get() shouldBe 1
                    parallellKjøringer.get() shouldBe 1
                    registry.sistVellykket("parallell") shouldBeGreaterThan 0.0
                }
            }
        } finally {
            executor.stop()
        }

        registry.sistVellykket("seriell") shouldBe 0.0
    }

    private fun startJob(
        meterRegistry: MeterRegistry,
        clock: Clock,
        task: suspend (CorrelationId) -> TaskResultat,
    ): GruppertTaskExecutor = GruppertTaskExecutor.startJob(
        runCheckFactory = alltidLeder(),
        grupper = nonEmptyListOf(
            TaskGruppe(
                navn = "gruppe",
                intervall = 1.days,
                tasks = nonEmptyListOf(task),
                initialDelay = kotlin.time.Duration.ZERO,
            ),
        ),
        mdcCallIdKey = "test",
        clock = clock,
        meterRegistry = meterRegistry,
    )

    private fun alltidLeder(): RunCheckFactory = RunCheckFactory(
        leaderPodLookup = object : LeaderPodLookup {
            override fun amITheLeader(localHostName: String) = true.right()
        },
        isReady = { true },
    )

    private fun ventTilKjørt(kjøringer: AtomicInteger) {
        runBlocking {
            eventually(10.seconds) {
                kjøringer.get() shouldBe 1
            }
        }
    }

    private fun MeterRegistry.sistVellykket(prosess: String): Double =
        get("tpts.bakgrunnsprosess.sist_vellykket_tidspunkt_sekunder")
            .tags("prosess", prosess, "type", "jobb")
            .gauge()
            .value()

    private fun MeterRegistry.intervall(prosess: String): Double =
        get("tpts.bakgrunnsprosess.intervall_sekunder")
            .tags("prosess", prosess, "type", "jobb")
            .gauge()
            .value()
}
