package no.nav.tiltakspenger.libs.jobber

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.tiltakspenger.libs.common.CorrelationId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class StoppableJobTest {
    private val logger: KLogger = KotlinLogging.logger { }

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
    fun `skal kjøre startAt-overload med default debugging logging`() {
        val executionLatch = CountDownLatch(1)
        val startAt = Date.from(Instant.now().plusMillis(50))

        val stoppableJob = startStoppableJob(
            jobName = "test-job",
            startAt = startAt,
            intervall = Duration.ofDays(1),
            logger = logger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = emptyList(),
        ) {
            executionLatch.countDown()
        }

        try {
            executionLatch.await(1, TimeUnit.SECONDS) shouldBe true
        } finally {
            stoppableJob.stop()
        }
    }

    @Test
    fun `skal logge debug før og etter jobb når debugging logging er aktivert`() {
        val mockLogger = mockLogger()
        val scheduler = ManuellScheduler()
        val antallKjoringer = AtomicInteger(0)

        val stoppableJob = startStoppableJob(
            jobName = "test-job",
            log = mockLogger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = emptyList(),
            job = { antallKjoringer.incrementAndGet() },
            enableDebuggingLogging = true,
            scheduleJob = scheduler::schedule,
        )

        try {
            scheduler.runJob()

            antallKjoringer.get() shouldBe 1
            verify(exactly = 2) { mockLogger.debug(any<() -> Any?>()) }
        } finally {
            stoppableJob.stop()
        }
    }

    @Test
    fun `skal logge debug når runJobCheck stopper jobb`() {
        val mockLogger = mockLogger()
        val scheduler = ManuellScheduler()

        val stoppableJob = startStoppableJob(
            jobName = "test-job",
            log = mockLogger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = listOf(
                object : RunJobCheck {
                    override fun shouldRun(): Boolean = false
                },
            ),
            job = { error("Jobben skal ikke kjøres") },
            enableDebuggingLogging = true,
            scheduleJob = scheduler::schedule,
        )

        try {
            scheduler.runJob()

            verify(exactly = 1) { mockLogger.debug(any<() -> Any?>()) }
        } finally {
            stoppableJob.stop()
        }
    }

    @Test
    fun `skal ikke logge debug ved vellykket jobb når debugging logging er deaktivert`() {
        val mockLogger = mockLogger()
        val scheduler = ManuellScheduler()

        val stoppableJob = startStoppableJob(
            jobName = "test-job",
            log = mockLogger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = emptyList(),
            job = { },
            enableDebuggingLogging = false,
            scheduleJob = scheduler::schedule,
        )

        try {
            scheduler.runJob()

            verify(exactly = 0) { mockLogger.debug(any<() -> Any?>()) }
        } finally {
            stoppableJob.stop()
        }
    }

    @Test
    fun `skal logge feil når jobb kaster exception`() {
        val mockLogger = mockLogger()
        val scheduler = ManuellScheduler()

        val stoppableJob = startStoppableJob(
            jobName = "test-job",
            log = mockLogger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = emptyList(),
            job = { throw IllegalStateException("boom") },
            enableDebuggingLogging = false,
            scheduleJob = scheduler::schedule,
        )

        try {
            scheduler.runJob()

            verify(exactly = 1) { mockLogger.error(any<Throwable>(), any<() -> Any?>()) }
        } finally {
            stoppableJob.stop()
        }
    }

    @Test
    fun `skal logge feil når stop kaster exception`() {
        val mockLogger = mockLogger()
        val scheduler = ManuellScheduler(
            timer = object : Timer("failing-stop", true) {
                override fun cancel() {
                    throw IllegalStateException("boom")
                }
            },
        )

        startStoppableJob(
            jobName = "test-job",
            log = mockLogger,
            mdcCallIdKey = "test-call-id",
            runJobCheck = emptyList(),
            job = { },
            enableDebuggingLogging = false,
            scheduleJob = scheduler::schedule,
        ).stop()

        verify(exactly = 1) { mockLogger.error(any<Throwable>(), any<() -> Any?>()) }
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

    private fun mockLogger(): KLogger {
        return mockk(relaxed = true) {
            every { info(any<() -> Any?>()) } answers {
                firstArg<() -> Any?>().invoke()
            }
            every { debug(any<() -> Any?>()) } answers {
                firstArg<() -> Any?>().invoke()
            }
            every { error(any<Throwable>(), any<() -> Any?>()) } answers {
                secondArg<() -> Any?>().invoke()
            }
        }
    }

    private class ManuellScheduler(
        private val timer: Timer = Timer("manual-scheduler", true),
    ) {
        private var timerTask: TimerTask? = null

        fun schedule(task: TimerTask.() -> Unit): Timer {
            timerTask = object : TimerTask() {
                override fun run() {
                    task()
                }
            }
            return timer
        }

        fun runJob() {
            checkNotNull(timerTask) { "Forventet at en TimerTask var registrert." }.run()
        }
    }
}
