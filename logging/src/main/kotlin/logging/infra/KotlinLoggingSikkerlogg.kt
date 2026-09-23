package no.nav.tiltakspenger.libs.logging.infra

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KMarkerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

/**
 * Implementasjonen av [Sikkerlogg] basert på kotlin-logging.
 * Logger med TEAM_LOGS-markøren, som nais ruter til team-logs.
 *
 * @param appNavn Appens navn slik det står i container_name-labelen i GCP, typisk fra `NAIS_APP_NAME` via konsumentens konfigurasjon.
 * @param gcpProsjektId Teamets GCP-prosjekt for miljøet, typisk fra `GCP_TEAM_PROJECT_ID` via konsumentens konfigurasjon.
 * Uten begge verdiene blir [seSikkerlogg] ren tekst uten lenke (lokalt/test).
 */
class KotlinLoggingSikkerlogg(
    appNavn: String? = null,
    gcpProsjektId: String? = null,
) : Sikkerlogg {
    private val sikkerLogger: KLogger = KotlinLogging.logger("team-logs-logger")
    private val sikkerMarker = KMarkerFactory.getMarker("TEAM_LOGS")

    override val seSikkerlogg: String = if (appNavn == null || gcpProsjektId == null) {
        "Se sikkerlogg for mer kontekst."
    } else {
        "Se sikkerlogg for mer kontekst: " +
            "https://console.cloud.google.com/logs/query;query=resource.labels.container_name%3D%22$appNavn%22?project=$gcpProsjektId"
    }

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
