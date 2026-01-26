package no.nav.tiltakspenger.libs.persistering.infrastruktur

import kotliquery.Session
import kotliquery.TransactionalSession
import no.nav.tiltakspenger.libs.persistering.domene.SessionContext
import no.nav.tiltakspenger.libs.persistering.domene.SessionFactory
import no.nav.tiltakspenger.libs.persistering.domene.TransactionContext
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresSessionContext.Companion.withSession
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresTransactionContext.Companion.withTransaction
import javax.sql.DataSource

/**
 * Bør på sikt flyttes til common/infrastructure/persistence
 */
class PostgresSessionFactory(
    private val dataSource: DataSource,
    private val sessionCounter: SessionCounter,
) : SessionFactory {

    /** Lager en ny context - starter ikke sesjonen. */
    private fun newSessionContext(): PostgresSessionContext {
        return PostgresSessionContext(dataSource, sessionCounter)
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    override fun <T> withSessionContext(action: (SessionContext) -> T): T {
        return newSessionContext().let { context ->
            context.withSession {
                action(context)
            }
        }
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    override fun <T> withSessionContext(sessionContext: SessionContext?, action: (SessionContext) -> T): T {
        return (sessionContext ?: newSessionContext()).let { context ->
            context.withSession {
                action(context)
            }
        }
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withSession(
        disableSessionCounter: Boolean = false,
        action: (Session) -> T,
    ): T {
        return newSessionContext().let { context ->
            context.withSession(disableSessionCounter = disableSessionCounter) {
                action(it)
            }
        }
    }

    /** Gjenbruker [sessionContext] hvis den ikke er null, ellers lages en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withSession(
        sessionContext: SessionContext?,
        disableSessionCounter: Boolean = false,
        action: (Session) -> T,
    ): T {
        return withSessionContext(sessionContext) { context ->
            context.withSession(disableSessionCounter = disableSessionCounter) {
                action(it)
            }
        }
    }

    /**
     * Lager en ny context - starter ikke sesjonen.
     *
     * Merk: Man må kalle withTransaction {...} før man kaller withSession {...} hvis ikke får man en [IllegalStateException]
     * withSession {...} vil kjøre inne i den samme transaksjonen.
     * */
    private fun newTransactionContext(): PostgresTransactionContext {
        return PostgresTransactionContext(dataSource, sessionCounter)
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    override fun <T> withTransactionContext(
        action: (TransactionContext) -> T,
    ): T {
        return newTransactionContext().let { context ->
            context.withTransaction {
                action(context)
            }
        }
    }

    /** Lager en ny context dersom den ikke finnes og starter sesjonen - lukkes automatisk  */
    override fun <T> withTransactionContext(
        transactionContext: TransactionContext?,
        action: (TransactionContext) -> T,
    ): T {
        return (transactionContext ?: newTransactionContext()).let { context ->
            context.withTransaction {
                action(context)
            }
        }
    }

    override fun <T> use(transactionContext: TransactionContext, action: (TransactionContext) -> T): T {
        return transactionContext.withTransaction {
            action(transactionContext)
        }
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withTransaction(action: (TransactionalSession) -> T): T {
        return newTransactionContext().let { context ->
            context.withTransaction {
                action(it)
            }
        }
    }

    /** Lager en ny context dersom den ikke finnes og starter sesjonen - lukkes automatisk  */
    fun <T> withTransaction(
        transactionContext: TransactionContext?,
        action: (TransactionalSession) -> T,
    ): T {
        return withTransactionContext(transactionContext) {
            it.withTransaction {
                action(it)
            }
        }
    }
}
