package no.nav.tiltakspenger.libs.jobber

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.CorrelationId
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Merk: testene kjører mot **reell tid**, ikke virtuell tid.
 * [GruppertTaskExecutor] skedulerer på en intern coroutine-scope ([Skeduleringsscope] med [kotlinx.coroutines.Dispatchers.IO]) som ikke deler dispatcher med testtråden, så `runTest`/virtuell tid kan ikke drive den fremover.
 * Derfor venter vi i sanntid med kotest sine `eventually` (poll til assertion passerer) og `continually` (verifiser at en assertion holder gjennom hele vinduet) i stedet for faste `Thread.sleep`, slik at testene verken bruker mer tid enn nødvendig eller blir flaky.
 */
internal class GruppertTaskExecutorTest {

    private val clock = Clock.systemUTC()

    private fun alltidLeder(): RunCheckFactory = RunCheckFactory(
        leaderPodLookup = object : LeaderPodLookup {
            override fun amITheLeader(localHostName: String) = true.right()
        },
        isReady = { true },
    )

    private val ferdig: suspend (CorrelationId) -> TaskResultat = { TaskResultat.Ferdig }

    private val ingenArbeid: suspend (CorrelationId) -> TaskResultat = { TaskResultat.IngenArbeid }

    /** Trådsikker logback-appender som samler opp loggmeldingene fra executoren. */
    private class LoggFanger : AppenderBase<ILoggingEvent>() {
        val meldinger = ConcurrentLinkedQueue<String>()

        override fun append(hendelse: ILoggingEvent) {
            meldinger.add(hendelse.formattedMessage)
        }
    }

    /** Lager en [KLogger] med unikt [navn] hvor alt som logges på debug og over fanges av den returnerte [LoggFanger]. */
    private fun loggerMedFanger(navn: String): Pair<KLogger, LoggFanger> {
        val underliggende = LoggerFactory.getLogger(navn) as ch.qos.logback.classic.Logger
        underliggende.level = Level.DEBUG
        val fanger = LoggFanger().apply { start() }
        underliggende.addAppender(fanger)
        return KotlinLogging.logger(navn) to fanger
    }

    @Test
    fun `to serielle grupper med ulike intervaller kjører aldri samtidig`() {
        val running = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        fun task(): suspend (CorrelationId) -> TaskResultat = { _ ->
            val current = running.incrementAndGet()
            maxConcurrent.updateAndGet { it.coerceAtLeast(current) }
            delay(15.milliseconds)
            running.decrementAndGet()
            TaskResultat.Ferdig
        }

        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "A", intervall = 20.milliseconds, tasks = nonEmptyListOf(task()), initialDelay = 5.milliseconds),
                TaskGruppe(navn = "B", intervall = 35.milliseconds, tasks = nonEmptyListOf(task()), initialDelay = 5.milliseconds),
            ),
        )
        // Verifiser kontinuerlig i sanntid at aldri mer enn én task kjører samtidig mens begge de serielle gruppene kjører flere sykluser.
        runBlocking {
            continually(300.milliseconds) {
                maxConcurrent.get() shouldBeLessThanOrEqualTo 1
            }
        }
        executor.stop()

        // Minst én task skal ha kjørt (og aldri mer enn én samtidig).
        maxConcurrent.get() shouldBe 1
    }

    @Test
    fun `parallell gruppe kjører uavhengig av en treg seriell gruppe`() {
        val running = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val parallellKjørt = AtomicInteger(0)

        val tregSeriell: suspend (CorrelationId) -> TaskResultat = { _ ->
            val current = running.incrementAndGet()
            maxConcurrent.updateAndGet { it.coerceAtLeast(current) }
            delay(200.milliseconds)
            running.decrementAndGet()
            TaskResultat.Ferdig
        }
        val parallell: suspend (CorrelationId) -> TaskResultat = { _ ->
            val current = running.incrementAndGet()
            maxConcurrent.updateAndGet { it.coerceAtLeast(current) }
            parallellKjørt.incrementAndGet()
            delay(10.milliseconds)
            running.decrementAndGet()
            TaskResultat.Ferdig
        }

        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "treg", intervall = 20.milliseconds, tasks = nonEmptyListOf(tregSeriell), initialDelay = 5.milliseconds),
                TaskGruppe(navn = "rask", intervall = 20.milliseconds, tasks = nonEmptyListOf(parallell), initialDelay = 5.milliseconds, kjøremodus = Kjøremodus.PARALLELT),
            ),
        )
        // Vent i sanntid til den parallelle gruppen har overlappet den trege serielle.
        runBlocking {
            eventually(10.seconds) {
                // Den parallelle gruppen skal ha kjørt flere ganger mens den serielle henger i sin lange task, og ha overlappet den (maxConcurrent >= 2 beviser at de kjørte samtidig).
                parallellKjørt.get() shouldBeGreaterThanOrEqualTo 3
                maxConcurrent.get() shouldBeGreaterThanOrEqualTo 2
            }
        }
        executor.stop()
    }

    @Test
    fun `kjørKontinuerligTilTom kjører på nytt umiddelbart til det ikke er mer arbeid`() {
        val kjøringer = AtomicInteger(0)
        // Meld MerArbeid de 4 første gangene, deretter Ferdig.
        val task: suspend (CorrelationId) -> TaskResultat = { _ ->
            val n = kjøringer.incrementAndGet()
            if (n <= 4) TaskResultat.MerArbeid else TaskResultat.Ferdig
        }

        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                // Stort intervall: uten drenering ville vi bare fått 1 kjøring i vinduet under.
                TaskGruppe(
                    navn = "drener",
                    intervall = 10_000.milliseconds,
                    tasks = nonEmptyListOf(task),
                    kjøremodus = Kjøremodus.PARALLELT,
                    initialDelay = 5.milliseconds,
                    kjørKontinuerligTilTom = true,
                ),
            ),
        )
        // Vent i sanntid til gruppen har drenert alle MerArbeid-rundene.
        runBlocking {
            eventually(10.seconds) {
                // Skal ha drenert 4 MerArbeid-runder + minst 1 Ferdig-runde raskt etter hverandre, uten å vente intervallet.
                kjøringer.get() shouldBeGreaterThanOrEqualTo 5
            }
        }
        executor.stop()
    }

    @Test
    fun `tasks i en gruppe kjører serielt i listerekkefølge og en feil stopper ikke de øvrige`() {
        val hendelser = ConcurrentLinkedQueue<String>()
        val ranB = AtomicInteger(0)

        val taskA: suspend (CorrelationId) -> TaskResultat = { _ ->
            hendelser.add("A")
            error("forventet feil i A")
        }
        val taskB: suspend (CorrelationId) -> TaskResultat = { _ ->
            hendelser.add("B")
            ranB.incrementAndGet()
            TaskResultat.Ferdig
        }

        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "gruppe", intervall = 30.milliseconds, tasks = nonEmptyListOf(taskA, taskB), initialDelay = 5.milliseconds),
            ),
        )
        // Vent i sanntid til minst to sykluser med A→B har kjørt.
        runBlocking {
            eventually(10.seconds) {
                ranB.get() shouldBeGreaterThanOrEqualTo 2
            }
        }
        executor.stop()

        // Rekkefølgen skal alltid være A før B innen en syklus.
        val liste = hendelser.toList()
        liste shouldHaveAtLeastSize 4
        liste.chunked(2).filter { it.size == 2 }.forEach { it shouldBe listOf("A", "B") }
    }

    @Test
    fun `alle tasks i samme gruppesyklus deler correlationId`() {
        val ids = ConcurrentLinkedQueue<CorrelationId>()
        val taskA: suspend (CorrelationId) -> TaskResultat = { id ->
            ids.add(id)
            TaskResultat.Ferdig
        }
        val taskB: suspend (CorrelationId) -> TaskResultat = { id ->
            ids.add(id)
            TaskResultat.Ferdig
        }

        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "gruppe", intervall = 30.milliseconds, tasks = nonEmptyListOf(taskA, taskB), initialDelay = 5.milliseconds),
            ),
        )
        // Vent i sanntid til minst én syklus med A og B har kjørt.
        runBlocking {
            eventually(10.seconds) {
                ids.toList() shouldHaveAtLeastSize 2
            }
        }
        executor.stop()

        val liste = ids.toList()
        liste shouldHaveAtLeastSize 2
        // Task A og B i første syklus deler id.
        liste[0] shouldBe liste[1]
    }

    @Test
    fun `en gruppe kjører ikke når poden ikke er leder`() {
        val kjøringer = AtomicInteger(0)
        val ikkeLeder = RunCheckFactory(
            leaderPodLookup = object : LeaderPodLookup {
                override fun amITheLeader(localHostName: String) = false.right()
            },
            isReady = { true },
        )

        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = ikkeLeder,
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                TaskGruppe(
                    navn = "gruppe",
                    intervall = 20.milliseconds,
                    tasks = nonEmptyListOf({
                        kjøringer.incrementAndGet()
                        TaskResultat.Ferdig
                    }),
                    initialDelay = 5.milliseconds,
                ),
                TaskGruppe(
                    navn = "parallell",
                    intervall = 20.milliseconds,
                    tasks = nonEmptyListOf({
                        kjøringer.incrementAndGet()
                        TaskResultat.Ferdig
                    }),
                    initialDelay = 5.milliseconds,
                    kjøremodus = Kjøremodus.PARALLELT,
                ),
            ),
        )
        // Verifiser i sanntid at ingenting kjører siden poden ikke er leder.
        runBlocking {
            continually(200.milliseconds) {
                kjøringer.get() shouldBe 0
            }
        }
        executor.stop()

        kjøringer.get() shouldBe 0
    }

    @Test
    fun `stop lar pågående syklus kjøre ferdig og er idempotent`() {
        val startet = AtomicInteger(0)
        val fullført = AtomicInteger(0)
        val task: suspend (CorrelationId) -> TaskResultat = { _ ->
            startet.incrementAndGet()
            delay(80.milliseconds)
            fullført.incrementAndGet()
            TaskResultat.Ferdig
        }

        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "parallell", intervall = 20.milliseconds, tasks = nonEmptyListOf(task), initialDelay = 5.milliseconds, kjøremodus = Kjøremodus.PARALLELT),
            ),
        )
        // Vent i sanntid til en kjøring har startet, og stopp mens den pågår.
        runBlocking {
            eventually(10.seconds) {
                startet.get() shouldBeGreaterThanOrEqualTo 1
            }
        }
        executor.stop() // skal blokkere til pågående kjøring er ferdig
        executor.stop() // idempotent

        startet.get() shouldBe fullført.get()
        startet.get() shouldBeGreaterThanOrEqualTo 1
    }

    @Test
    fun `stop midt i kontinuerlig drenering starter ingen ny batch selv om det er mer arbeid`() {
        // Tasken melder alltid MerArbeid (uendelig backlog) og bruker litt tid per batch, slik at stop() garantert treffer mens en batch pågår.
        val startet = AtomicInteger(0)
        val fullført = AtomicInteger(0)
        val task: suspend (CorrelationId) -> TaskResultat = { _ ->
            startet.incrementAndGet()
            delay(40.milliseconds)
            fullført.incrementAndGet()
            TaskResultat.MerArbeid
        }

        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                TaskGruppe(
                    navn = "drener",
                    intervall = 10.milliseconds,
                    tasks = nonEmptyListOf(task),
                    kjøremodus = Kjøremodus.PARALLELT,
                    initialDelay = 5.milliseconds,
                    kjørKontinuerligTilTom = true,
                ),
            ),
        )
        // Vent i sanntid til minst én batch har startet, og stopp mens dreneringen pågår.
        runBlocking {
            eventually(10.seconds) {
                startet.get() shouldBeGreaterThanOrEqualTo 1
            }
        }
        executor.stop() // blokkerer til pågående batch er ferdig

        // stop() venter på pågående batch, så alle startede batcher skal være fullført (ingen halv batch).
        startet.get() shouldBe fullført.get()
        val startedeVedStop = startet.get()
        startedeVedStop shouldBeGreaterThanOrEqualTo 1

        // Etter stop skal ingen ny batch starte, selv om tasken hele tiden melder MerArbeid.
        runBlocking {
            continually(100.milliseconds) {
                startet.get() shouldBe startedeVedStop
            }
        }
    }

    @Test
    fun `serielle grupper med likt intervall der alle tasks melder IngenArbeid logger én samlet debuglinje per runde`() {
        val (logger, fanger) = loggerMedFanger("ingen-arbeid-samlet-test")
        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            logger = logger,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "A", intervall = 25.milliseconds, tasks = nonEmptyListOf(ingenArbeid), initialDelay = 5.milliseconds),
                TaskGruppe(navn = "B", intervall = 25.milliseconds, tasks = nonEmptyListOf(ingenArbeid), initialDelay = 5.milliseconds),
            ),
        )
        // Vent i sanntid til en runde uten arbeid er logget; at begge gruppenavnene står i samme linje beviser at gruppene holder seg i samme runde.
        runBlocking {
            eventually(10.seconds) {
                fanger.meldinger.any { it == "Ingen av jobbene hadde arbeid i denne runden: A, B" } shouldBe true
            }
        }
        executor.stop()
    }

    @Test
    fun `runde der en av gruppene hadde arbeid logger ikke ingen-arbeid-linjen`() {
        val (logger, fanger) = loggerMedFanger("ingen-arbeid-blandet-test")
        val kjøringer = AtomicInteger(0)
        val medArbeid: suspend (CorrelationId) -> TaskResultat = { _ ->
            kjøringer.incrementAndGet()
            TaskResultat.Ferdig
        }
        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            logger = logger,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "medArbeid", intervall = 20.milliseconds, tasks = nonEmptyListOf(medArbeid), initialDelay = 5.milliseconds),
                TaskGruppe(navn = "utenArbeid", intervall = 20.milliseconds, tasks = nonEmptyListOf(ingenArbeid), initialDelay = 5.milliseconds),
            ),
        )
        // Vent i sanntid til flere runder har kjørt, slik at fraværet av linjen faktisk beviser noe.
        runBlocking {
            eventually(10.seconds) {
                kjøringer.get() shouldBeGreaterThanOrEqualTo 3
            }
        }
        executor.stop()

        fanger.meldinger.any { it.startsWith("Ingen av jobbene hadde arbeid") } shouldBe false
    }

    @Test
    fun `runde der en task feiler logger ikke ingen-arbeid-linjen selv om resten melder IngenArbeid`() {
        val (logger, fanger) = loggerMedFanger("ingen-arbeid-feil-test")
        val kjøringer = AtomicInteger(0)
        val feiler: suspend (CorrelationId) -> TaskResultat = { _ ->
            kjøringer.incrementAndGet()
            error("forventet feil")
        }
        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            logger = logger,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "gruppe", intervall = 20.milliseconds, tasks = nonEmptyListOf(feiler, ingenArbeid), initialDelay = 5.milliseconds),
            ),
        )
        // Vent i sanntid til flere runder har kjørt, slik at fraværet av linjen faktisk beviser noe.
        runBlocking {
            eventually(10.seconds) {
                kjøringer.get() shouldBeGreaterThanOrEqualTo 3
            }
        }
        executor.stop()

        fanger.meldinger.any { it.startsWith("Ingen av jobbene hadde arbeid") } shouldBe false
    }

    @Test
    fun `runde der en task melder Feilet logger ikke ingen-arbeid-linjen selv om resten melder IngenArbeid`() {
        val (logger, fanger) = loggerMedFanger("ingen-arbeid-feilet-status-test")
        val kjøringer = AtomicInteger(0)
        val feilet: suspend (CorrelationId) -> TaskResultat = { _ ->
            kjøringer.incrementAndGet()
            TaskResultat.Feilet
        }
        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            logger = logger,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "gruppe", intervall = 20.milliseconds, tasks = nonEmptyListOf(feilet, ingenArbeid), initialDelay = 5.milliseconds),
            ),
        )
        // Vent i sanntid til flere runder har kjørt, slik at fraværet av linjen faktisk beviser noe.
        runBlocking {
            eventually(10.seconds) {
                kjøringer.get() shouldBeGreaterThanOrEqualTo 3
            }
        }
        executor.stop()

        fanger.meldinger.any { it.startsWith("Ingen av jobbene hadde arbeid") } shouldBe false
    }

    @Test
    fun `parallell gruppe uten arbeid logger egen debuglinje`() {
        val (logger, fanger) = loggerMedFanger("ingen-arbeid-parallell-test")
        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            logger = logger,
            grupper = nonEmptyListOf(
                TaskGruppe(navn = "p", intervall = 20.milliseconds, tasks = nonEmptyListOf(ingenArbeid), initialDelay = 5.milliseconds, kjøremodus = Kjøremodus.PARALLELT),
            ),
        )
        // Vent i sanntid til en runde uten arbeid er logget for den parallelle gruppen.
        runBlocking {
            eventually(10.seconds) {
                fanger.meldinger.any { it == "Jobben 'p' hadde ikke arbeid i denne runden." } shouldBe true
            }
        }
        executor.stop()
    }

    @Test
    fun `STANDARD_JOBB_INTERVALL er 10 sekunder`() {
        GruppertTaskExecutor.STANDARD_JOBB_INTERVALL shouldBe 10.seconds
    }

    @Test
    fun `validerer input`() {
        shouldThrow<IllegalArgumentException> {
            GruppertTaskExecutor.startJob(
                alltidLeder(),
                grupper = nonEmptyListOf(
                    TaskGruppe("dup", 10.milliseconds, nonEmptyListOf(ferdig)),
                    TaskGruppe("dup", 10.milliseconds, nonEmptyListOf(ferdig)),
                ),
                mdcCallIdKey = "test",
                clock = clock,
            )
        }
        shouldThrow<IllegalArgumentException> {
            GruppertTaskExecutor.startJob(
                alltidLeder(),
                grupper = nonEmptyListOf(TaskGruppe("a", 0.milliseconds, nonEmptyListOf(ferdig))),
                mdcCallIdKey = "test",
                clock = clock,
            )
        }
        shouldThrow<IllegalArgumentException> {
            GruppertTaskExecutor.startJob(
                alltidLeder(),
                grupper = nonEmptyListOf(TaskGruppe("a", 10.milliseconds, nonEmptyListOf(ferdig), initialDelay = (-1).milliseconds)),
                mdcCallIdKey = "test",
                clock = clock,
            )
        }
    }

    @Test
    fun `bruker isReady-sjekken fra RunCheckFactory`() {
        // Dekker at IsReady faktisk kobles inn (left => hopp over).
        val kjøringer = AtomicInteger(0)
        val ikkeKlar = RunCheckFactory(
            leaderPodLookup = object : LeaderPodLookup {
                override fun amITheLeader(localHostName: String) = true.right()
            },
            isReady = { false },
        )
        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = ikkeKlar,
            mdcCallIdKey = "test",
            clock = clock,
            grupper = nonEmptyListOf(
                TaskGruppe(
                    "a",
                    20.milliseconds,
                    nonEmptyListOf({
                        kjøringer.incrementAndGet()
                        TaskResultat.Ferdig
                    }),
                    initialDelay = 5.milliseconds,
                ),
            ),
        )
        // Verifiser i sanntid at ingenting kjører siden poden ikke er klar.
        runBlocking {
            continually(200.milliseconds) {
                kjøringer.get() shouldBe 0
            }
        }
        executor.stop()
        kjøringer.get() shouldBe 0
    }

    @Test
    fun `custom runJobCheck respekteres`() {
        // Sikrer at en eksplisitt runJobCheck-liste brukes.
        val kjøringer = AtomicInteger(0)
        val nekt = object : RunJobCheck {
            override fun shouldRun() = JobbSkalIkkeKjøre.IkkeKlar.left()
        }
        val executor = GruppertTaskExecutor.startJob(
            runCheckFactory = alltidLeder(),
            mdcCallIdKey = "test",
            clock = clock,
            runJobCheck = listOf(nekt),
            grupper = nonEmptyListOf(
                TaskGruppe(
                    "a",
                    20.milliseconds,
                    nonEmptyListOf({
                        kjøringer.incrementAndGet()
                        TaskResultat.Ferdig
                    }),
                    initialDelay = 5.milliseconds,
                ),
            ),
        )
        // Verifiser i sanntid at ingenting kjører siden runJobCheck nekter.
        runBlocking {
            continually(200.milliseconds) {
                kjøringer.get() shouldBe 0
            }
        }
        executor.stop()
        kjøringer.get() shouldBe 0
    }
}
