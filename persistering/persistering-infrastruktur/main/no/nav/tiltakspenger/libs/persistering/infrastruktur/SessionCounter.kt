package no.nav.tiltakspenger.libs.persistering.infrastruktur

import io.github.oshai.kotlinlogging.KLogger
import kotlin.concurrent.getOrSet

class SessionCounter(
    private val logger: KLogger,
    private val whenOverThreshold: (numberOfSession: Int) -> Unit = {
        logger.error(RuntimeException("Genererer en stacktrace for enklere debugging.")) { "Sessions per thread over threshold: We started a new session while a session for this thread was already open. Total number of session: $it." }
    },
) {
    private val activeSessionsPerThread: ThreadLocal<Int> = ThreadLocal()

    suspend fun <T> withCountSessions(action: suspend () -> T): T {
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
