package no.nav.tiltakspenger.libs.kafka.test

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import java.time.Clock
import java.time.Duration
import java.time.Instant

fun eventually(
    until: Duration = Duration.ofSeconds(3),
    interval: Duration = Duration.ofMillis(100),
    clock: Clock,
    func: () -> Unit,
) = runBlocking {
    val deadline = Instant.now(clock).plusNanos(until.toNanos())

    var throwable: Throwable = IllegalStateException()

    while (Instant.now(clock).isBefore(deadline)) {
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
