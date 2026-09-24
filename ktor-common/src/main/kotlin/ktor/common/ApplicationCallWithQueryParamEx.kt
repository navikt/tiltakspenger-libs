package no.nav.tiltakspenger.libs.ktor.common

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.application.ApplicationCall

/**
 * @param errorMessage denne svares ut og logges - ikke send inn noe personsensitivt
 * @param errorCode denne svares ut og logges - ikke send inn noe personsensitivt
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun <T> ApplicationCall.withQueryParam(
    paramName: String,
    noinline parse: (String) -> T,
    errorMessage: String,
    errorCode: String,
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (T) -> Unit,
) {
    parseQueryParam(
        paramName = paramName,
        parse = parse,
        errorMessage = errorMessage,
        errorCode = errorCode,
        logger = logger,
        loggTilSikkerlogg = loggTilSikkerlogg,
    ).fold(
        ifLeft = { this.respond400BadRequest(it) },
        ifRight = { onSuccess(it) },
    )
}

/**
 * @param errorMessage denne svares ut og logges - ikke send inn noe personsensitivt
 * @param errorCode denne svares ut og logges - ikke send inn noe personsensitivt
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun <T> ApplicationCall.withOptionalQueryParam(
    paramName: String,
    noinline parse: (String) -> T,
    errorMessage: String,
    errorCode: String,
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (T?) -> Unit,
) {
    parseOptionalQueryParam(
        paramName = paramName,
        parse = parse,
        errorMessage = errorMessage,
        errorCode = errorCode,
        logger = logger,
        loggTilSikkerlogg = loggTilSikkerlogg,
    ).fold(
        ifLeft = { this.respond400BadRequest(it) },
        ifRight = { onSuccess(it) },
    )
}
