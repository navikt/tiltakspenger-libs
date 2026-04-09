package no.nav.tiltakspenger.libs.kafka

import no.nav.tiltakspenger.libs.kafka.config.MAX_POLL_INTERVAL_MS
import kotlin.math.min

class ConsumerStatus(
    private val baseDelayMillis: Long = 500L,
    private val initialDelayMillis: Long = 1000L,
) {
    companion object {
        const val MAX_DELAY = MAX_POLL_INTERVAL_MS - 60_000L
    }

    private var _retries: Int = 0

    val retries: Int get() = _retries

    val isFailure: Boolean get() = retries > 0

    val backoffDuration: Long
        get() {
            val delay = baseDelayMillis * retries * retries + initialDelayMillis
            return min(delay, MAX_DELAY)
        }

    fun success() {
        _retries = 0
    }

    fun failure() {
        _retries++
    }
}
