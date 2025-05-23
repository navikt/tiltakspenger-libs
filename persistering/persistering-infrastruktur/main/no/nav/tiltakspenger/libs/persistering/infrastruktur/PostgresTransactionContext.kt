package no.nav.tiltakspenger.libs.persistering.infrastruktur

import arrow.core.Either
import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import kotliquery.using
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

    private val log = KotlinLogging.logger {}

    // Det er viktig at sesjoner ikke opprettes utenfor en try-with-resource, som kan føre til connection-lekkasjer.
    private var transactionalSession: TransactionalSession? = null

    companion object {

        fun <T> TransactionContext?.withTransaction(
            sessionFactory: SessionFactory,
            action: (session: TransactionalSession) -> T,
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
        fun <T> TransactionContext.withTransaction(
            disableSessionCounter: Boolean = false,
            action: (TransactionalSession) -> T,
        ): T {
            this as PostgresTransactionContext
            return if (transactionalSession == null) {
                // Vi ønsker kun at den ytterste blokka lukker sesjonen (using)
                using(
                    sessionOf(
                        dataSource = dataSource,
                        returnGeneratedKey = true,
                        strict = true,
                    ),
                ) { session ->
                    session.transaction { transactionalSession ->
                        this.transactionalSession = transactionalSession
                        if (disableSessionCounter) {
                            action(transactionalSession)
                        } else {
                            sessionCounter.withCountSessions {
                                action(transactionalSession)
                            }
                        }
                    }
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
        fun <T> TransactionContext.withSession(action: (session: Session) -> T): T {
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
}
