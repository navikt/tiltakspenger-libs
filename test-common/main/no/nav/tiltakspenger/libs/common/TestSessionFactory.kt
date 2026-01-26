package no.nav.tiltakspenger.libs.common

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

    override suspend fun <T> withSessionContext(action: suspend (SessionContext) -> T): T =
        SessionCounter().withCountSessions {
            action(
                sessionContext,
            )
        }

    override suspend fun <T> withSessionContext(
        sessionContext: SessionContext?,
        action: suspend (SessionContext) -> T,
    ): T =
        SessionCounter().withCountSessions {
            action(sessionContext ?: Companion.sessionContext)
        }

    override suspend fun <T> withTransactionContext(action: suspend (TransactionContext) -> T): T =
        SessionCounter().withCountSessions { action(transactionContext) }

    override suspend fun <T> withTransactionContext(
        transactionContext: TransactionContext?,
        action: suspend (TransactionContext) -> T,
    ): T =
        SessionCounter().withCountSessions {
            action(transactionContext ?: Companion.transactionContext)
        }

    override suspend fun <T> use(
        transactionContext: TransactionContext,
        action: suspend (TransactionContext) -> T,
    ): T =
        SessionCounter().withCountSessions {
            action(transactionContext)
        }

    fun newSessionContext() = sessionContext

    fun newTransactionContext() = transactionContext

    // TODO jah: Denne er duplikat med den som ligger i database siden test-common ikke har en referanse til database-modulen.
    private class SessionCounter {
        private val activeSessionsPerThread: ThreadLocal<Int> = ThreadLocal()

        suspend fun <T> withCountSessions(action: suspend () -> T): T =
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
