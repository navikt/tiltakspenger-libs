package no.nav.tiltakspenger.libs.persistering.infrastruktur

import mu.KLogger
import kotlin.concurrent.getOrSet

class SessionCounter(
    private val logger: KLogger,
    private val whenOverThreshold: (numberOfSession: Int) -> Unit = {
        logger.error(
            "Sessions per thread over threshold: We started a new session while a session for this thread was already open. Total number of session: $it.",
            RuntimeException("Genererer en stacktrace for enklere debugging."),
        )
    },
) {
    private val activeSessionsPerThread: ThreadLocal<Int> = ThreadLocal()

    fun <T> withCountSessions(action: () -> T): T {
        return activeSessionsPerThread.getOrSet { 0 }.inc().let {
            if (it > 1) {
                whenOverThreshold(it)
            }
            activeSessionsPerThread.set(it)
            try {
                action()
            } finally {
                activeSessionsPerThread.set(activeSessionsPerThread.getOrSet { 1 }.dec())
            }
        }
    }
}
