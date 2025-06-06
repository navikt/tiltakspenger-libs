package no.nav.tiltakspenger.libs.jobber

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.CorrelationId
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class StoppableJobTest {
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `neste jobb skal ikke starte før første er ferdig`() {
        val logger: KLogger = KotlinLogging.logger { }
        val executionTimes: ConcurrentLinkedQueue<Long> = ConcurrentLinkedQueue()
        val executionLatch = CountDownLatch(2)
        val initialDelay = Duration.ofMillis(0)
        val interval = Duration.ofMillis(5)

        val job: (CorrelationId) -> Unit = { correlationId ->
            executionTimes.add(System.currentTimeMillis())
            when (executionLatch.count) {
                2L -> {
                    executionLatch.count shouldBe 2
                    // bruker lengre tid enn interval
                    Thread.sleep(20)
                    executionLatch.count shouldBe 2
                }

                1L -> {
                    // Selvom den forrige jobben tar lengre tid enn intervallet, skal den ikke starte før den er ferdig
                    executionLatch.count shouldBe 1
                }
            }
            executionLatch.countDown()
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
            val completed = executionLatch.await(50, TimeUnit.MILLISECONDS)
            completed shouldBe true
            executionTimes shouldHaveSize 2

            val executionTimesAsList = executionTimes.toList()
            val firstExecutionStart = executionTimesAsList[0]
            val secondExecutionStart = executionTimesAsList[1]

            val timeBetweenExecutions = secondExecutionStart - firstExecutionStart
            // kommentar jah: hvis vi får timing issues får vi bare minke dette tallet.
            timeBetweenExecutions shouldBeGreaterThan 20
        } finally {
            stoppableJob.stop()
        }
    }
}
