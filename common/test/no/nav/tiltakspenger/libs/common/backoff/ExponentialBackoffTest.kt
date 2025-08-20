package no.nav.tiltakspenger.libs.common.backoff

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.plus
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

internal class ExponentialBackoffTest {
    val delayTable: Map<Long, Duration> = mapOf(
        1L to 1.minutes.toJavaDuration(),
        2L to 5.minutes.toJavaDuration(),
        3L to 15.minutes.toJavaDuration(),
        4L to 30.minutes.toJavaDuration(),
        5L to 1.hours.toJavaDuration(),
        6L to 2.hours.toJavaDuration(),
        7L to 4.hours.toJavaDuration(),
        8L to 8.hours.toJavaDuration(),
        9L to 12.hours.toJavaDuration(),
        10L to 24.hours.toJavaDuration(),
    )

    val maxDelay = 24.hours.toJavaDuration()

    @Test
    fun `Skal prøve på nytt ved 0 forsøk`() {
        val (shouldRetry, nextRetryTime) = LocalDateTime.now(fixedClock).shouldRetry(0, fixedClock)
        shouldRetry shouldBe true
        nextRetryTime shouldBe LocalDateTime.now(fixedClock)
    }

    @Test
    fun `Skal ikke forsøke på nytt ved 1 forsøk og mindre enn 1 minutt har passert`() {
        val clockAfter = fixedClock.plus(59, ChronoUnit.SECONDS)
        val (shouldRetry, nextRetryTime) = LocalDateTime.now(fixedClock).shouldRetry(1, clockAfter)
        shouldRetry shouldBe false
        nextRetryTime shouldBe LocalDateTime.now(fixedClock).plus(delayTable[1L])
    }

    @Test
    fun `Skal forsøke på nytt ved 1 forsøk og nøyaktig 1 minutt har passert`() {
        val clockAfter = fixedClock.plus(1, ChronoUnit.MINUTES)
        val (shouldRetry, nextRetryTime) = LocalDateTime.now(fixedClock).shouldRetry(1, clockAfter)
        shouldRetry shouldBe true
        nextRetryTime shouldBe LocalDateTime.now(fixedClock).plus(delayTable[1L])
    }

    @Test
    fun `Skal forsøke på nytt ved 1 forsøk og mer enn 1 minutt har passert`() {
        val clockAfter = fixedClock.plus(1, ChronoUnit.DAYS)
        val (shouldRetry, nextRetryTime) = LocalDateTime.now(fixedClock).shouldRetry(1, clockAfter)
        shouldRetry shouldBe true
        nextRetryTime shouldBe LocalDateTime.now(fixedClock).plus(delayTable[1L])
    }

    @Test
    fun `Skal ikke forsøke på nytt ved 2 forsøk og det mindre enn 5 minutt har passert`() {
        val clockAfter = fixedClock.plus(299, ChronoUnit.SECONDS)
        val (shouldRetry, nextRetryTime) = LocalDateTime.now(fixedClock).shouldRetry(2, clockAfter)
        shouldRetry shouldBe false
        nextRetryTime shouldBe LocalDateTime.now(fixedClock).plus(delayTable[2L])
    }

    @Test
    fun `Skal forsøke på nytt ved 2 forsøk og nøyaktig 5 minutt har passert`() {
        val clockAfter = fixedClock.plus(5, ChronoUnit.MINUTES)
        val (shouldRetry, nextRetryTime) = LocalDateTime.now(fixedClock).shouldRetry(2, clockAfter)
        shouldRetry shouldBe true
        nextRetryTime shouldBe LocalDateTime.now(fixedClock).plus(delayTable[2L])
    }

    @Test
    fun `Skal ikke forsøke på nytt om maks ventetid ikke har passert`() {
        val clockAfter = fixedClock
            .plus(23, ChronoUnit.HOURS)
            .plus(59, ChronoUnit.MINUTES)
        val (shouldRetry, nextRetryTime) = LocalDateTime.now(fixedClock).shouldRetry(11, clockAfter)
        shouldRetry shouldBe false
        nextRetryTime shouldBe LocalDateTime.now(fixedClock).plus(maxDelay)
    }

    @Test
    fun `Skal forsøke på nytt om maks ventetid har passert`() {
        val clockAfter = fixedClock.plus(24, ChronoUnit.HOURS)
        val (shouldRetry, nextRetryTime) = LocalDateTime.now(fixedClock).shouldRetry(11, clockAfter)
        shouldRetry shouldBe true
        nextRetryTime shouldBe LocalDateTime.now(fixedClock).plus(maxDelay)
    }
}
