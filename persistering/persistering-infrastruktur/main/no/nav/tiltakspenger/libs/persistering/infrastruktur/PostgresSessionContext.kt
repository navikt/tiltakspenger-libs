package no.nav.tiltakspenger.libs.persistering.infrastruktur

import arrow.core.Either
import arrow.core.getOrElse
import kotliquery.Session
import kotliquery.sessionOf
import kotliquery.using
import no.nav.tiltakspenger.libs.persistering.domene.SessionContext
import no.nav.tiltakspenger.libs.persistering.domene.SessionFactory
import no.nav.tiltakspenger.libs.persistering.domene.TransactionContext
import org.slf4j.LoggerFactory
import javax.sql.DataSource
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresTransactionContext.Companion.withSession as transactionContextWithSession

/**
 * Bør på sikt flyttes til common/infrastructure/persistence
 */
open class PostgresSessionContext(
    private val dataSource: DataSource,
    private val sessionCounter: SessionCounter,
) : SessionContext {

    private val log = LoggerFactory.getLogger(this::class.java)

    // Det er viktig at sesjoner ikke opprettes utenfor en try-with-resource, som kan føre til connection-lekkasjer.
    private var session: Session? = null

    companion object {

        fun <T> SessionContext?.withSession(
            sessionFactory: SessionFactory,
            action: (session: Session) -> T,
        ): T {
            return sessionFactory.withSessionContext(this) { sessionContext ->
                sessionContext.withSession { session -> action(session) }
            }
        }

        /**
         * Første kall lager en ny sesjon og lukkes automatisk sammen med funksjonsblokka.
         * Påfølgende kall gjenbruker samme sesjon.
         *
         * @param disableSessionCounter Lagt til for at SimuleringStub ikke skal trigge 'Sessions per thread over threshold'. Kan fjernes dersom man finner en bedre løsning.
         * @throws IllegalStateException dersom sesjonen er lukket.
         */
        // Dette er en extension function og ikke en funksjon i interfacet siden vi ikke ønsker en referanse til Session, som er infrastrukturspesifikt, i domenelaget.
        fun <T> SessionContext.withSession(
            disableSessionCounter: Boolean = false,
            action: (session: Session) -> T,
        ): T {
            if (this is TransactionContext) {
                return this.transactionContextWithSession(action)
            }
            this as PostgresSessionContext
            return if (session == null) {
                // Vi ønsker kun at den ytterste blokka lukker sesjonen (using)
                using(
                    sessionOf(
                        dataSource = dataSource,
                        returnGeneratedKey = true,
                    ).also { session = it },
                ) {
                    if (disableSessionCounter) {
                        action(it)
                    } else {
                        sessionCounter.withCountSessions {
                            action(it)
                        }
                    }
                }
            } else {
                if (isClosed()) {
                    throw IllegalStateException("Sesjonen er lukket.")
                }
                action(session!!)
            }
        }
    }

    /**
     * @return true dersom 1) den aldri har vært åpnet 2) er lukket 3) en feil skjedde.
     */
    override fun isClosed(): Boolean {
        if (session == null) return true
        return Either.catch { session!!.connection.underlying.isClosed }.getOrElse {
            log.error("En feil skjedde når vi prøvde å sjekke om sesjonen var lukket", it)
            true
        }
    }
}
