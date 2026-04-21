package no.nav.tiltakspenger.libs.ktor.common

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.application.ApplicationCall
import no.nav.tiltakspenger.libs.common.MeldekortId
import no.nav.tiltakspenger.libs.common.RammebehandlingId
import no.nav.tiltakspenger.libs.common.SakId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.VedtakId

/**
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun <reified T> ApplicationCall.withBody(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline ifRight: suspend (T) -> Unit,
) {
    parseBody<T>(logger = logger, loggTilSikkerlogg = loggTilSikkerlogg).fold(
        ifLeft = { this.respond400BadRequest(it) },
        ifRight = { ifRight(it) },
    )
}

/**
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 * @param errorMessage denne svares ut og logges - ikke send inn noe personsensitivt
 * @param errorCode denne svares ut og logges - ikke send inn noe personsensitivt
 */
suspend inline fun <T> ApplicationCall.withValidParam(
    paramName: String,
    noinline parse: (String) -> T,
    errorMessage: String,
    errorCode: String,
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (T) -> Unit,
) {
    parseParam(
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
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun ApplicationCall.withRammebehandlingId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (RammebehandlingId) -> Unit,
) {
    parseRammebehandlingId(logger = logger, loggTilSikkerlogg = loggTilSikkerlogg).fold(
        ifLeft = { this.respond400BadRequest(it) },
        ifRight = { onSuccess(it) },
    )
}

/**
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun ApplicationCall.withSakId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (SakId) -> Unit,
) {
    parseSakId(logger = logger, loggTilSikkerlogg = loggTilSikkerlogg).fold(
        ifLeft = { this.respond400BadRequest(it) },
        ifRight = { onSuccess(it) },
    )
}

/**
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun ApplicationCall.withSøknadId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (SøknadId) -> Unit,
) {
    parseSøknadId(logger = logger, loggTilSikkerlogg = loggTilSikkerlogg).fold(
        ifLeft = { this.respond400BadRequest(it) },
        ifRight = { onSuccess(it) },
    )
}

/**
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun ApplicationCall.withMeldekortId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (MeldekortId) -> Unit,
) {
    parseMeldekortId(logger = logger, loggTilSikkerlogg = loggTilSikkerlogg).fold(
        ifLeft = { this.respond400BadRequest(it) },
        ifRight = { onSuccess(it) },
    )
}

/**
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun ApplicationCall.withVedtakId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (VedtakId) -> Unit,
) {
    parseVedtakId(logger = logger, loggTilSikkerlogg = loggTilSikkerlogg).fold(
        ifLeft = { this.respond400BadRequest(it) },
        ifRight = { onSuccess(it) },
    )
}
