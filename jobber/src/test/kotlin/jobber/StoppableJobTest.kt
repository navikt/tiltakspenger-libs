package no.nav.tiltakspenger.libs.jobber

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.CorrelationId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class StoppableJobTest {
    private val logger: KLogger = KotlinLogging.logger { }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `neste jobb skal ikke starte før første er ferdig`() {
        val executionTimes: ConcurrentLinkedQueue<Long> = ConcurrentLinkedQueue()
        val executionLatch = CountDownLatch(2)
        val executionCounter = AtomicInteger(0)
        val initialDelay = Duration.ofMillis(0)
        val interval = Duration.ofMillis(10)

        val job: (CorrelationId) -> Unit = { _ ->
            when (executionCounter.incrementAndGet()) {
                1 -> {
                    executionTimes.add(System.currentTimeMillis())
                    // bruker lengre tid enn interval
                    Thread.sleep(50)
                    executionLatch.count shouldBe 2
                    executionLatch.countDown()
                }

                2 -> {
                    executionTimes.add(System.currentTimeMillis())
                    // Selvom den forrige jobben tar lengre tid enn intervallet, skal den ikke starte før den er ferdig
                    executionLatch.count shouldBe 1
                    executionLatch.countDown()
                }
            }
        }
        val stoppableJob = startStoppableJob(
            jobName = "test-job",
            initialDelay = initialDelay,
            intervall = interval,
            logger = logger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = emptyList(),
            enableDebuggingLogging = false,
            runAsDaemon = true,
            job = job,
        )

        try {
            val completed = executionLatch.await(200, TimeUnit.MILLISECONDS)
            completed shouldBe true
            stoppableJob.stop()
            executionTimes shouldHaveSize 2

            val executionTimesAsList = executionTimes.toList()
            val firstExecutionStart = executionTimesAsList[0]
            val secondExecutionStart = executionTimesAsList[1]

            val timeBetweenExecutions = secondExecutionStart - firstExecutionStart
            // kommentar jah: hvis vi får timing issues får vi bare minke dette tallet.
            timeBetweenExecutions shouldBeGreaterThan 40
        } finally {
            stoppableJob.stop()
        }
    }

    @Test
    fun `skal ikke kjøre jobb når runJobCheck returnerer false`() {
        val antallKjoringer = AtomicInteger(0)
        val stoppableJob = startStoppableJob(
            jobName = "test-job",
            initialDelay = Duration.ZERO,
            intervall = Duration.ofMillis(10),
            logger = logger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = listOf(
                object : RunJobCheck {
                    override fun shouldRun(): Boolean = false
                },
            ),
            enableDebuggingLogging = false,
            runAsDaemon = true,
        ) {
            antallKjoringer.incrementAndGet()
        }

        try {
            Thread.sleep(60)
            antallKjoringer.get() shouldBe 0
        } finally {
            stoppableJob.stop()
        }
    }

    @Test
    fun `skal vente til startAt før første kjøring`() {
        val clock = Clock.systemUTC()
        val firstExecutionTime = AtomicLong(0)
        val executionLatch = CountDownLatch(1)
        val startAt = Date.from(Instant.now(clock).plusMillis(150))

        val stoppableJob = startStoppableJob(
            jobName = "test-job",
            startAt = startAt,
            intervall = Duration.ofDays(1),
            logger = logger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = emptyList(),
            enableDebuggingLogging = false,
        ) {
            firstExecutionTime.compareAndSet(0, clock.millis())
            executionLatch.countDown()
        }

        try {
            Thread.sleep(75)
            executionLatch.count shouldBe 1

            val completed = executionLatch.await(1, TimeUnit.SECONDS)
            completed shouldBe true
            firstExecutionTime.get() shouldBeGreaterThan startAt.time - 1
        } finally {
            stoppableJob.stop()
        }
    }

    @Test
    fun `skal feile raskt ved ugyldig intervall`() {
        shouldThrowWithMessage<IllegalArgumentException>("intervall må være større enn 0.") {
            startStoppableJob(
                jobName = "test-job",
                initialDelay = Duration.ZERO,
                intervall = Duration.ZERO,
                logger = logger,
                mdcCallIdKey = "test-call-id",
                runJobCheck = emptyList(),
                enableDebuggingLogging = false,
                runAsDaemon = true,
            ) { }
        }
    }

    @Test
    fun `skal feile raskt ved negativ initial delay`() {
        shouldThrowWithMessage<IllegalArgumentException>("initialDelay kan ikke være negativ.") {
            startStoppableJob(
                jobName = "test-job",
                initialDelay = Duration.ofMillis(-1),
                intervall = Duration.ofMillis(10),
                logger = logger,
                mdcCallIdKey = "test-call-id",
                runJobCheck = emptyList(),
                enableDebuggingLogging = false,
                runAsDaemon = true,
            ) { }
        }
    }
}
