package no.nav.tiltakspenger.libs.httpklient

import io.github.oshai.kotlinlogging.KLogger
import no.nav.tiltakspenger.libs.httpklient.retry.RetryOutcome
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import java.net.URI

internal fun HttpKlientLoggingConfig.logSuccess(
    request: HttpKlientRequest,
    statusCode: Int,
    requestHeaders: Map<String, List<String>>,
    responseHeaders: Map<String, List<String>>,
) {
    logg(
        suksessNivå,
        sikkerloggMessage = { melding(request, statusCode, requestHeaders, responseHeaders, visSensitivt = true) },
    ) { melding(request, statusCode, requestHeaders, responseHeaders, visSensitivt = false) }
}

internal fun HttpKlientLoggingConfig.logUventetStatus(
    request: HttpKlientRequest,
    error: HttpKlientError.UventetStatus,
) {
    val nivå = if (error.statusCode in 400..499) klientfeilNivå else serverfeilNivå
    logg(
        nivå,
        sikkerloggMessage = {
            "${melding(request, error.statusCode, error.metadata.requestHeaders, error.metadata.responseHeaders, visSensitivt = true)} body=${error.body}"
        },
    ) { melding(request, error.statusCode, error.metadata.requestHeaders, error.metadata.responseHeaders, visSensitivt = false) }
}

internal fun HttpKlientLoggingConfig.logError(
    request: HttpKlientRequest,
    error: HttpKlientError,
) {
    logg(
        feilNivå,
        sikkerloggMessage = { melding(request, null, error.metadata.requestHeaders, error.metadata.responseHeaders, visSensitivt = true) },
    ) { melding(request, null, error.metadata.requestHeaders, error.metadata.responseHeaders, visSensitivt = false) }
}

/**
 * Diagnostikk-logg for når en skip-cache-retry ikke hjalp: et ferskt token ble også avvist (typisk persistent `401`/`403`).
 * Selve responsen logges allerede via [logUventetStatus]; dette er et supplement som gjør at klienter der dette skjer i loop-aktig volum kan oppdages.
 * Går via samme [melding]/[logg]-flyt som resten av modulen, slik at logger, `Sikkerlogg`, PII-redaksjon og [HttpKlientLoggingConfig.skipCacheRetryNivå] gjelder konsekvent.
 */
internal fun HttpKlientLoggingConfig.logSkipCacheRetryOppgitt(
    request: HttpKlientRequest,
    error: HttpKlientError,
) {
    val suffix = " – ferskt auth-token ble også avvist etter skip-cache-retry"
    logg(
        skipCacheRetryNivå,
        sikkerloggMessage = { melding(request, error.metadata.statusCode, error.metadata.requestHeaders, error.metadata.responseHeaders, visSensitivt = true) + suffix },
    ) { melding(request, error.metadata.statusCode, error.metadata.requestHeaders, error.metadata.responseHeaders, visSensitivt = false) + suffix }
}

/**
 * Default-varsel om overdreven retry-bruk, brukt når [no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig.onExcessiveRetries] ikke er satt.
 * Går via samme [logg]-flyt som resten av modulen, slik at logger, `Sikkerlogg` og [HttpKlientLoggingConfig.excessiveRetriesNivå] gjelder konsekvent — skrur du av logging, er også dette stille.
 * En konsument som trenger å reagere på dataen selv setter en egen `onExcessiveRetries`-hook og får hele [RetryOutcome] i stedet.
 */
internal fun HttpKlientLoggingConfig.logExcessiveRetries(outcome: RetryOutcome) {
    logg(excessiveRetriesNivå) {
        "HTTP-klient brukte ${outcome.attempts} forsøk (totalt ${outcome.totalDuration}). " +
            "Vurder å øke timeout eller undersøke nedstrømsstabilitet."
    }
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

/**
 * Bygger loggmeldingen.
 * [visSensitivt] styrer PII-eksponering: `false` er den trygge varianten som går til den vanlige [HttpKlientLoggingConfig.logger] (URI uten query/fragment, alle header-verdier maskert), mens `true` er `Sikkerlogg`-varianten som viser full URI og faktiske header-verdier (hemmeligheter som auth/cookie maskeres uansett).
 */
private fun HttpKlientLoggingConfig.melding(
    request: HttpKlientRequest,
    statusCode: Int?,
    requestHeaders: Map<String, List<String>>,
    responseHeaders: Map<String, List<String>>,
    visSensitivt: Boolean,
): String {
    val uri = if (visSensitivt) request.uri.toString() else request.uri.utenQueryOgFragment()
    val status = statusCode?.let { " status=$it" }.orEmpty()
    val headers = if (inkluderHeadere) {
        val req = if (visSensitivt) requestHeaders.redactSensitiveHeaders() else requestHeaders.maskAllHeaderValues()
        val resp = if (visSensitivt) responseHeaders.redactSensitiveHeaders() else responseHeaders.maskAllHeaderValues()
        " requestHeaders=$req responseHeaders=$resp"
    } else {
        ""
    }
    return "HTTP ${request.method} $uri$status$headers"
}

/**
 * Scheme + host + port + path, uten query, fragment og userinfo.
 * Brukes for den vanlige loggen slik at eventuelle sensitive query-parametre (eller userinfo) ikke lekker; full URI logges kun til `Sikkerlogg`.
 */
private fun URI.utenQueryOgFragment(): String = buildString {
    scheme?.let { append(it).append("://") }
    host?.let { append(it) }
    if (port != -1) append(":").append(port)
    append(rawPath.orEmpty())
}
