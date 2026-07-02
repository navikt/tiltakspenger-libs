package no.nav.tiltakspenger.libs.httpklient

import io.github.oshai.kotlinlogging.KLogger
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

internal fun HttpKlientLoggingConfig.logSuccess(
    request: HttpKlientRequest,
    statusCode: Int,
    requestHeaders: Map<String, List<String>>,
    responseHeaders: Map<String, List<String>>,
) {
    val message = melding(request, statusCode, requestHeaders, responseHeaders)
    logg(suksessNivå) { message }
}

internal fun HttpKlientLoggingConfig.logUventetStatus(
    request: HttpKlientRequest,
    error: HttpKlientError.UventetStatus,
) {
    val nivå = if (error.statusCode in 400..499) klientfeilNivå else serverfeilNivå
    val message = melding(request, error.statusCode, error.metadata.requestHeaders, error.metadata.responseHeaders)
    logg(nivå, sikkerloggMessage = { "$message body=${error.body}" }) { message }
}

internal fun HttpKlientLoggingConfig.logError(
    request: HttpKlientRequest,
    error: HttpKlientError,
) {
    val message = melding(request, null, error.metadata.requestHeaders, error.metadata.responseHeaders)
    logg(feilNivå) { message }
}

/**
 * Logger [message] på det oppgitte [nivå]et til [HttpKlientLoggingConfig.logger] og — når [HttpKlientLoggingConfig.loggTilSikkerlogg] er `true` — til `Sikkerlogg`.
 * [HttpKlientLogNivå.OFF] betyr ingen logging for denne kategorien.
 * [sikkerloggMessage] lar Sikkerlogg-varianten inkludere ekstra (potensielt sensitiv) detalj, f.eks. responsbody; den defaulter til [message].
 */
private fun HttpKlientLoggingConfig.logg(
    nivå: HttpKlientLogNivå,
    sikkerloggMessage: () -> Any? = { null },
    message: () -> Any?,
) {
    if (nivå == HttpKlientLogNivå.OFF) return
    logger?.loggPå(nivå, message)
    if (loggTilSikkerlogg) {
        val sikker = sikkerloggMessage().let { it ?: message() }
        sikkerloggPå(nivå) { sikker }
    }
}

private fun KLogger.loggPå(nivå: HttpKlientLogNivå, message: () -> Any?) {
    when (nivå) {
        HttpKlientLogNivå.OFF -> {}
        HttpKlientLogNivå.TRACE -> trace(message)
        HttpKlientLogNivå.DEBUG -> debug(message)
        HttpKlientLogNivå.INFO -> info(message)
        HttpKlientLogNivå.WARN -> warn(message)
        HttpKlientLogNivå.ERROR -> error(message)
    }
}

private fun sikkerloggPå(nivå: HttpKlientLogNivå, message: () -> Any?) {
    when (nivå) {
        HttpKlientLogNivå.OFF -> {}
        HttpKlientLogNivå.TRACE -> Sikkerlogg.trace(loggstatement = message)
        HttpKlientLogNivå.DEBUG -> Sikkerlogg.debug(loggstatement = message)
        HttpKlientLogNivå.INFO -> Sikkerlogg.info(loggstatement = message)
        HttpKlientLogNivå.WARN -> Sikkerlogg.warn(loggstatement = message)
        HttpKlientLogNivå.ERROR -> Sikkerlogg.error(loggstatement = message)
    }
}

private fun HttpKlientLoggingConfig.melding(
    request: HttpKlientRequest,
    statusCode: Int?,
    requestHeaders: Map<String, List<String>>,
    responseHeaders: Map<String, List<String>>,
): String {
    val status = statusCode?.let { " status=$it" }.orEmpty()
    val headers = if (inkluderHeadere) {
        " requestHeaders=${requestHeaders.redactSensitiveHeaders()} responseHeaders=${responseHeaders.redactSensitiveHeaders()}"
    } else {
        ""
    }
    return "HTTP ${request.method} ${request.uri}$status$headers"
}
