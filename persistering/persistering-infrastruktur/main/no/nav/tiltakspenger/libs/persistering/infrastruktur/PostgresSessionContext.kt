package no.nav.tiltakspenger.libs.persistering.infrastruktur

import arrow.core.Either
import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotliquery.Session
import kotliquery.sessionOf
import no.nav.tiltakspenger.libs.persistering.domene.SessionContext
import no.nav.tiltakspenger.libs.persistering.domene.SessionFactory
import no.nav.tiltakspenger.libs.persistering.domene.TransactionContext
import javax.sql.DataSource
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresTransactionContext.Companion.withSession as transactionContextWithSession

/**
 * Bør på sikt flyttes til common/infrastructure/persistence
 */
open class PostgresSessionContext(
    private val dataSource: DataSource,
    private val sessionCounter: SessionCounter,
) : SessionContext {

    private val log = KotlinLogging.logger {}

    // Det er viktig at sesjoner ikke opprettes utenfor en try-with-resource, som kan føre til connection-lekkasjer.
    private var session: Session? = null

    companion object {

        suspend fun <T> SessionContext?.withSession(
            sessionFactory: SessionFactory,
            action: suspend (session: Session) -> T,
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
        suspend fun <T> SessionContext.withSession(
            disableSessionCounter: Boolean = false,
            action: suspend (session: Session) -> T,
        ): T = withContext(Dispatchers.IO) {
            if (this@withSession is TransactionContext) {
                return@withContext this@withSession.transactionContextWithSession(action)
            }
            this@withSession as PostgresSessionContext

            if (session == null) {
                val newSession = sessionOf(
                    dataSource = dataSource,
                    returnGeneratedKey = true,
                    strict = true,
                ).also { session = it }

                try {
                    if (disableSessionCounter) {
                        action(newSession)
                    } else {
                        sessionCounter.withCountSessions {
                            action(newSession)
                        }
                    }
                } finally {
                    newSession.close()
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
            log.error(it) { "En feil skjedde når vi prøvde å sjekke om sesjonen var lukket" }
            true
        }
    }
}
