package no.nav.tiltakspenger.libs.persistering.infrastruktur

import arrow.core.Either
import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import no.nav.tiltakspenger.libs.persistering.domene.SessionFactory
import no.nav.tiltakspenger.libs.persistering.domene.TransactionContext
import javax.sql.DataSource

/**
 * Holder en transaksjon på tvers av repo-kall.
 * Ikke tråd-sikker.
 * Bør på sikt flyttes til common/infrastructure/persistence
 */
class PostgresTransactionContext(
    private val dataSource: DataSource,
    private val sessionCounter: SessionCounter,
) : TransactionContext {

    private val onSuccessCallbacks = listOf<suspend () -> Unit>()
    private val onErrorCallbacks = listOf<suspend (Throwable) -> Unit>()

    private val log = KotlinLogging.logger {}

    // Det er viktig at sesjoner ikke opprettes utenfor en try-with-resource, som kan føre til connection-lekkasjer.
    private var transactionalSession: TransactionalSession? = null

    companion object {

        suspend fun <T> TransactionContext?.withTransaction(
            sessionFactory: SessionFactory,
            action: suspend (session: TransactionalSession) -> T,
        ): T {
            return sessionFactory.withTransactionContext(this) { transactionContext ->
                transactionContext.withTransaction { transactionalSession -> action(transactionalSession) }
            }
        }

        /**
         * Første kall lager en ny transaksjonell sesjon og lukkes automatisk sammen med funksjonsblokka..
         * Påfølgende kall gjenbruker samme transaksjon.
         *
         * @param disableSessionCounter Lagt til for at SimuleringStub ikke skal trigge 'Sessions per thread over threshold'. Kan fjernes dersom man finner en bedre løsning.
         * @throws IllegalStateException dersom den transaksjonelle sesjonen er lukket.
         */
        // Dette er en extension function og ikke en funksjon i interfacet siden vi ikke ønsker en referanse til Session, som er infrastrukturspesifikt, i domenelaget.
        suspend fun <T> TransactionContext.withTransaction(
            disableSessionCounter: Boolean = false,
            action: suspend (TransactionalSession) -> T,
        ): T = withContext(Dispatchers.IO) {
            this@withTransaction as PostgresTransactionContext

            if (transactionalSession == null) {
                val session = sessionOf(
                    dataSource = dataSource,
                    returnGeneratedKey = true,
                    strict = true,
                )
                val (result: T?, exception: Throwable?) = try {
                    session.transaction { transactionalSession ->
                        this@withTransaction.transactionalSession = transactionalSession
                        // Bruker runBlocking her for å kunne kalle suspend funksjonen action fra en ikke-suspend kontekst.
                        @Suppress("RunBlockingInSuspendFunction")
                        runBlocking {
                            if (disableSessionCounter) {
                                action(transactionalSession)
                            } else {
                                sessionCounter.withCountSessions {
                                    action(transactionalSession)
                                }
                            }
                        }
                    } to null
                } catch (ex: Throwable) {
                    null to ex
                } finally {
                    session.close()
                }

                if (exception != null) {
                    executeOnError(exception)
                    throw exception
                } else {
                    executeOnSuccess()
                    @Suppress("UNCHECKED_CAST")
                    result as T
                }
            } else {
                if (isClosed()) {
                    throw IllegalStateException("Den transaksjonelle sesjonen er lukket.")
                }
                action(transactionalSession!!)
            }
        }

        // Dette er en extension function og ikke en funksjon i interfacet siden vi ikke ønsker en referanse til Session, som er infrastrukturspesifikt, i domenelaget.

        /**
         * @throws IllegalStateException dersom man ikke har kalt [withTransaction] først eller den transaksjonelle sesjonen er lukket.
         */
        suspend fun <T> TransactionContext.withSession(action: suspend (session: Session) -> T): T {
            this as PostgresTransactionContext
            if (transactionalSession == null) {
                throw IllegalStateException("Må først starte en withTransaction(...) før man kan kalle withSession(...) for en TransactionContext.")
            }
            if (isClosed()) {
                throw IllegalStateException("Den transaksjonelle sesjonen er lukket.")
            }
            return action(transactionalSession!!)
        }
    }

    /**
     * @return true dersom 1) den aldri har vært åpnet 2) er lukket 3) en feil skjedde.
     */
    override fun isClosed(): Boolean {
        if (transactionalSession == null) return true
        return Either.catch { transactionalSession!!.connection.underlying.isClosed }.getOrElse {
            log.error(it) { "En feil skjedde når vi prøvde å sjekke om den den transaksjonelle sesjonen var lukket" }
            true
        }
    }

    override suspend fun onSuccess(action: suspend () -> Unit) {
        onSuccessCallbacks.plus(action)
    }

    override suspend fun onError(action: suspend (Throwable) -> Unit) {
        onErrorCallbacks.plus(action)
    }

    private suspend fun executeOnSuccess() {
        try {
            onSuccessCallbacks.forEach {
                try {
                    it()
                } catch (ex: Throwable) {
                    safeLog(ex) { "Forkaster feil som skjedde i et av elementene i onSuccess callback" }
                }
            }
        } catch (ex: Throwable) {
            safeLog(ex) { "Forkaster feil som skjedde ved iterering av callback" }
        }
    }

    private suspend fun executeOnError(throwable: Throwable) {
        try {
            onErrorCallbacks.forEach { callback ->
                try {
                    callback(throwable)
                } catch (ex: Throwable) {
                    safeLog(ex) { "Forkaster feil som skjedde i onErrorCallback" }
                }
            }
        } catch (ex: Throwable) {
            safeLog(ex) { "Forkaster feil som skjedde ved iterering av onErrorCallbacks" }
        }
    }

    private fun safeLog(ex: Throwable, message: () -> String) {
        try {
            log.error(ex, message)
        } catch (_: Throwable) {
            // På dette tidspunktet er det ikke mye vi kan gjøre dersom loggingen feiler.
        }
    }
}
