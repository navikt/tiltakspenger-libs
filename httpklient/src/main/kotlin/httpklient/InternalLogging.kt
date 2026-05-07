package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.logging.Sikkerlogg

internal fun HttpKlientLoggingConfig.logSuccess(
    request: BuiltHttpKlientRequest,
    statusCode: Int,
    requestHeaders: Map<String, List<String>>,
    responseHeaders: Map<String, List<String>>,
) {
    val message = melding(request, statusCode, requestHeaders, responseHeaders)
    logger?.info { message }
    if (loggTilSikkerlogg) {
        Sikkerlogg.info { message }
    }
}

internal fun HttpKlientLoggingConfig.logIkke2xx(
    request: BuiltHttpKlientRequest,
    error: HttpKlientError.Ikke2xx,
) {
    val message = melding(request, error.statusCode, error.metadata.requestHeaders, error.metadata.responseHeaders)
    if (error.statusCode in 400..499) {
        logger?.warn { message }
        if (loggTilSikkerlogg) {
            Sikkerlogg.warn { "$message body=${error.body}" }
        }
    } else {
        logger?.error { message }
        if (loggTilSikkerlogg) {
            Sikkerlogg.error { "$message body=${error.body}" }
        }
    }
}

internal fun HttpKlientLoggingConfig.logError(
    request: BuiltHttpKlientRequest,
    error: HttpKlientError,
) {
    val message = melding(request, null, error.metadata.requestHeaders, error.metadata.responseHeaders)
    logger?.error { message }
    if (loggTilSikkerlogg) {
        Sikkerlogg.error { message }
    }
}

private fun HttpKlientLoggingConfig.melding(
    request: BuiltHttpKlientRequest,
    statusCode: Int?,
    requestHeaders: Map<String, List<String>>,
    responseHeaders: Map<String, List<String>>,
): String {
    val status = statusCode?.let { " status=$it" }.orEmpty()
    val headers = if (inkluderHeadere) {
        " requestHeaders=$requestHeaders responseHeaders=$responseHeaders"
    } else {
        ""
    }
    return "HTTP ${request.method} ${request.uri}$status$headers"
}
