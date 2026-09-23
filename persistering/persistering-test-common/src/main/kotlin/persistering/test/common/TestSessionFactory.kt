package no.nav.tiltakspenger.libs.persistering.test.common

import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import no.nav.tiltakspenger.libs.persistering.domene.SessionContext
import no.nav.tiltakspenger.libs.persistering.domene.SessionFactory
import no.nav.tiltakspenger.libs.persistering.domene.TransactionContext
import kotlin.concurrent.getOrSet

class TestSessionFactory : SessionFactory {
    companion object {
        // Gjør det enklere å verifisere i testene.
        val sessionContext =
            object : SessionContext {
                override fun isClosed() = false
            }
        val transactionContext =
            object : TransactionContext {
                override fun isClosed() = false
                override suspend fun onSuccess(action: suspend () -> Unit) {
                    action()
                }

                override suspend fun onError(action: suspend (Throwable) -> Unit) {
                    action(RuntimeException("Opprettet for test"))
                }
            }
    }

    override fun <T> withSessionContext(disableSessionCounter: Boolean, action: (SessionContext) -> T): T =
        if (disableSessionCounter) {
            action(sessionContext)
        } else {
            SessionCounter().withCountSessions {
                action(
                    sessionContext,
                )
            }
        }

    override fun <T> withSessionContext(
        sessionContext: SessionContext?,
        disableSessionCounter: Boolean,
        action: (SessionContext) -> T,
    ): T =
        if (disableSessionCounter) {
            action(sessionContext ?: Companion.sessionContext)
        } else {
            SessionCounter().withCountSessions {
                action(sessionContext ?: Companion.sessionContext)
            }
        }

    override fun <T> withTransactionContext(disableSessionCounter: Boolean, action: (TransactionContext) -> T): T =
        if (disableSessionCounter) {
            action(transactionContext)
        } else {
            SessionCounter().withCountSessions { action(transactionContext) }
        }

    override fun <T> withTransactionContext(
        transactionContext: TransactionContext?,
        disableSessionCounter: Boolean,
        action: (TransactionContext) -> T,
    ): T =
        if (disableSessionCounter) {
            action(transactionContext ?: Companion.transactionContext)
        } else {
            SessionCounter().withCountSessions {
                action(transactionContext ?: Companion.transactionContext)
            }
        }

    override fun <T> use(
        transactionContext: TransactionContext,
        disableSessionCounter: Boolean,
        action: (TransactionContext) -> T,
    ): T =
        if (disableSessionCounter) {
            action(transactionContext)
        } else {
            SessionCounter().withCountSessions {
                action(transactionContext)
            }
        }

    fun newSessionContext() = sessionContext

    fun newTransactionContext() = transactionContext

    private class SessionCounter {
        private val activeSessionsPerThread: ThreadLocal<Int> = ThreadLocal()

        fun <T> withCountSessions(action: () -> T): T =
            activeSessionsPerThread.getOrSet { 0 }.inc().let {
                if (it > 1) {
                    fail("Database sessions were over the threshold while running test.")
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
