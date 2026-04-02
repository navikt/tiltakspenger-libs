package no.nav.tiltakspenger.libs.kafka.test

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.time.LocalDateTime

object AsyncUtils {
    fun eventually(
        until: Duration = Duration.ofSeconds(3),
        interval: Duration = Duration.ofMillis(100),
        func: () -> Unit,
    ) = no.nav.tiltakspenger.libs.kafka.test.eventually(until, interval, func)
}

fun eventually(
    until: Duration = Duration.ofSeconds(3),
    interval: Duration = Duration.ofMillis(100),
    func: () -> Unit,
) = runBlocking {
    val untilTime = LocalDateTime.now().plusNanos(until.toNanos())

    var throwable: Throwable = IllegalStateException()

    while (LocalDateTime.now().isBefore(untilTime)) {
        try {
            func()
            return@runBlocking
        } catch (t: Throwable) {
            throwable = t
            delay(interval)
        }
    }

    throw AssertionError(throwable)
}
