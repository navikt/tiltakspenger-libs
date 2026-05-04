package no.nav.tiltakspenger.libs.jobber

import arrow.core.right
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import kotlinx.coroutines.delay
import no.nav.tiltakspenger.libs.common.CorrelationId
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * Verifiserer at [TaskExecutor]:
 *  1. Kjører tasks innenfor en syklus seriellt og i listerekkefølgen.
 *  2. Aldri starter en ny syklus før forrige er ferdig (heller ikke for én og samme task).
 *  3. Lar det gå minst `intervall` mellom starten av to sykluser når syklusene er kortere enn intervallet.
 *  4. Lar en feil i én task verken stoppe etterfølgende tasks i samme syklus eller fremtidige sykluser.
 */
internal class TaskExecutorTest {

    private fun runCheckFactoryThatAlwaysAllowsRun(): RunCheckFactory {
        val attrs = Attributes()
        val isReadyKey = AttributeKey<Boolean>("isReady")
        attrs.put(isReadyKey, true)
        val leaderPodLookup = object : LeaderPodLookup {
            override fun amITheLeader(localHostName: String) = true.right()
        }
        return RunCheckFactory(
            leaderPodLookup = leaderPodLookup,
            attributes = attrs,
            isReadyKey = isReadyKey,
        )
    }

    @Test
    fun `tasks innenfor en syklus kjorer seriellt og i listerekkefolgen`() {
        val log = ConcurrentLinkedQueue<String>()
        val running = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        fun task(tag: String): suspend (CorrelationId) -> Unit = { _ ->
            val current = running.incrementAndGet()
            maxConcurrent.updateAndGet { it.coerceAtLeast(current) }
            log.add("$tag-start")
            delay(20.milliseconds)
            log.add("$tag-end")
            running.decrementAndGet()
        }

        val executor = TaskExecutor.startJob(
            runCheckFactory = runCheckFactoryThatAlwaysAllowsRun(),
            mdcCallIdKey = "test-call-id",
            tasks = listOf(task("A"), task("B"), task("C")),
            initialDelay = 10.milliseconds,
            intervall = 50.milliseconds,
        )
        Thread.sleep(600)
        executor.stop()

        // Maks én task kjører om gangen
        maxConcurrent.get() shouldBe 1

        // Hver fullstendige syklus skal være A, B, C i den rekkefølgen, uten overlapp.
        val entries = log.toList()
        val expectedCycle = listOf("A-start", "A-end", "B-start", "B-end", "C-start", "C-end")
        val fullCycles = entries.size / expectedCycle.size
        fullCycles shouldBeGreaterThanOrEqualTo 2
        repeat(fullCycles) { i ->
            val from = i * expectedCycle.size
            entries.subList(from, from + expectedCycle.size) shouldBe expectedCycle
        }
    }

    @Test
    fun `samme task kjorer aldri parallellt med seg selv selv om syklusen tar lengre enn intervallet`() {
        val running = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val cycleStarts = ConcurrentLinkedQueue<Long>()

        // Tasken tar lengre enn intervallet — Timer'en skal likevel ikke starte ny syklus før forrige er ferdig.
        val slowTask: suspend (CorrelationId) -> Unit = { _ ->
            cycleStarts.add(System.nanoTime())
            val current = running.incrementAndGet()
            maxConcurrent.updateAndGet { it.coerceAtLeast(current) }
            delay(150.milliseconds)
            running.decrementAndGet()
        }

        val executor = TaskExecutor.startJob(
            runCheckFactory = runCheckFactoryThatAlwaysAllowsRun(),
            mdcCallIdKey = "test-call-id",
            tasks = listOf(slowTask),
            initialDelay = 10.milliseconds,
            intervall = 30.milliseconds,
        )
        Thread.sleep(800)
        executor.stop()

        maxConcurrent.get() shouldBe 1
        cycleStarts.toList() shouldHaveAtLeastSize 2
    }

    @Test
    fun `det gar minst intervall millisekunder mellom starten av to sykluser`() {
        val cycleStarts = ConcurrentLinkedQueue<Long>()
        val task: suspend (CorrelationId) -> Unit = { _ ->
            cycleStarts.add(System.nanoTime())
            delay(10.milliseconds) // klart kortere enn intervallet
        }

        val intervalMs = 100L
        val executor = TaskExecutor.startJob(
            runCheckFactory = runCheckFactoryThatAlwaysAllowsRun(),
            mdcCallIdKey = "test-call-id",
            tasks = listOf(task),
            initialDelay = 10.milliseconds,
            intervall = intervalMs.milliseconds,
        )
        Thread.sleep(700)
        executor.stop()

        val starts = cycleStarts.toList()
        starts shouldHaveAtLeastSize 3
        val gapsMs = starts.zipWithNext { a, b -> (b - a) / 1_000_000 }
        // Liten toleranse (5 ms) for skedulerings-jitter i java.util.Timer.
        gapsMs.forEach { gap -> gap shouldBeGreaterThanOrEqualTo (intervalMs - 5) }
    }

    @Test
    fun `feil i en task stopper verken etterfolgende tasks i samme syklus eller fremtidige sykluser`() {
        val ranA = AtomicInteger(0)
        val ranB = AtomicInteger(0)

        val taskA: suspend (CorrelationId) -> Unit = { _ ->
            ranA.incrementAndGet()
            error("forventet feil i taskA")
        }
        val taskB: suspend (CorrelationId) -> Unit = { _ ->
            ranB.incrementAndGet()
        }

        val executor = TaskExecutor.startJob(
            runCheckFactory = runCheckFactoryThatAlwaysAllowsRun(),
            mdcCallIdKey = "test-call-id",
            tasks = listOf(taskA, taskB),
            initialDelay = 10.milliseconds,
            intervall = 40.milliseconds,
        )
        Thread.sleep(400)
        executor.stop()

        // Begge tasks skal ha kjørt minst to ganger til tross for at A alltid kaster.
        ranA.get() shouldBeGreaterThanOrEqualTo 2
        ranB.get() shouldBeGreaterThanOrEqualTo 2
        // Siden tasks kjører seriellt og Timer.cancel ikke avbryter pågående TimerTask, skal A og B ha samme antall.
        ranA.get() shouldBe ranB.get()
    }

    @Test
    fun `correlationId sendes til hver task og er lik for tasks i samme syklus`() {
        val correlationIds = ConcurrentLinkedQueue<Pair<String, CorrelationId>>()

        val taskA: suspend (CorrelationId) -> Unit = { correlationId ->
            correlationIds.add("A" to correlationId)
        }
        val taskB: suspend (CorrelationId) -> Unit = { correlationId ->
            correlationIds.add("B" to correlationId)
        }

        val executor = TaskExecutor.startJob(
            runCheckFactory = runCheckFactoryThatAlwaysAllowsRun(),
            mdcCallIdKey = "test-call-id",
            tasks = listOf(taskA, taskB),
            initialDelay = 10.milliseconds,
            intervall = 40.milliseconds,
        )
        Thread.sleep(200)
        executor.stop()

        val entries = correlationIds.toList()
        entries shouldHaveAtLeastSize 4
        val cycle1A = entries[0]
        val cycle1B = entries[1]
        val cycle2A = entries[2]
        cycle1A.first shouldBe "A"
        cycle1B.first shouldBe "B"
        // Tasks i samme syklus deler correlationId.
        cycle1A.second shouldBe cycle1B.second
        // Ulike sykluser har ulike correlationIds.
        cycle2A.second shouldNotBe cycle1A.second
    }
}
