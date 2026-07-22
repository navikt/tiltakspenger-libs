package no.nav.tiltakspenger.libs.logging.infra

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KMarkerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

/**
 * Implementasjonen av [Sikkerlogg] basert på kotlin-logging.
 * Logger med TEAM_LOGS-markøren, som nais ruter til team-logs.
 */
object KotlinLoggingSikkerlogg : Sikkerlogg {
    private val sikkerLogger: KLogger = KotlinLogging.logger("team-logs-logger")
    private val sikkerMarker = KMarkerFactory.getMarker("TEAM_LOGS")

    override fun trace(throwable: Throwable?, loggstatement: () -> Any?) {
        sikkerLogger.trace(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }

    override fun debug(throwable: Throwable?, loggstatement: () -> Any?) {
        sikkerLogger.debug(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }

    override fun info(throwable: Throwable?, loggstatement: () -> Any?) {
        sikkerLogger.info(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }

    override fun warn(throwable: Throwable?, loggstatement: () -> Any?) {
        sikkerLogger.warn(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }

    override fun error(throwable: Throwable?, loggstatement: () -> Any?) {
        sikkerLogger.error(throwable = throwable, marker = sikkerMarker, message = loggstatement)
    }
}
