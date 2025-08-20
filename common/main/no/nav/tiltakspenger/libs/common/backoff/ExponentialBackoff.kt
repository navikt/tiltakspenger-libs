package no.nav.tiltakspenger.libs.common.backoff

import java.time.Clock
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

fun LocalDateTime.shouldRetry(
    count: Long,
    clock: Clock,
    delayTable: Map<Long, Duration> = mapOf(
        1L to 1.minutes,
        2L to 5.minutes,
        3L to 15.minutes,
        4L to 30.minutes,
        5L to 1.hours,
        6L to 2.hours,
        7L to 4.hours,
        8L to 8.hours,
        9L to 12.hours,
        10L to 24.hours,
    ),
    maxDelay: Duration = 24.hours,
): Pair<Boolean, LocalDateTime> {
    if (count == 0L) return Pair(true, LocalDateTime.now(clock))
    val delayDuration = delayTable[count]?.coerceAtMost(maxDelay) ?: maxDelay
    val nextRetryTime = this.plus(delayDuration.toJavaDuration())
    return Pair<Boolean, LocalDateTime>(
        LocalDateTime.now(clock) >= nextRetryTime,
        nextRetryTime,
    )
}
