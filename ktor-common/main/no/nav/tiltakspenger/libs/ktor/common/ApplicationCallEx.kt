package no.nav.tiltakspenger.libs.ktor.common

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import no.nav.tiltakspenger.libs.common.BehandlingId
import no.nav.tiltakspenger.libs.common.MeldekortId
import no.nav.tiltakspenger.libs.common.SakId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.VedtakId
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

// Må være public pga. inline
val DEFAULT_APPLICATION_CALL_EX_LOGGER: KLogger by lazy { KotlinLogging.logger {} }

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
 * @param json ferdigserialisert JSON-string. Obs: Det gjøres ingen validering på om dette er gyldig JSON.
 *
 * Defaulter til 200 OK og Content-Type: application/json; charset=UTF-8
 */
suspend inline fun ApplicationCall.respondJsonString(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    this.respondText(
        text = json,
        contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8),
        status = status,
    )
}

/**
 * Defaulter til 200 OK og Content-Type: application/json; charset=UTF-8
 * @throws IllegalArgumentException hvis T er String. Bruk respondJsonString(json = ...) for ferdigserialiserte strenger
 */
suspend inline fun <reified T : Any> ApplicationCall.respondJson(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    require(value !is String) {
        "Bruk respondJsonString(json = ...) for ferdigserialiserte strenger"
    }
    respondJsonString(json = serialize(value), status = status)
}

/** Denne er lagt til for å få compile feil istedenfor runtime feil */
@Suppress("unused", "RedundantSuspendModifier", "UnusedReceiverParameter")
@Deprecated(
    message = "Bruk respondJson(json = ...) for ferdigserialiserte strenger",
    level = DeprecationLevel.ERROR,
)
suspend inline fun ApplicationCall.respondJson(
    value: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): Nothing = error("Bruk respondJson(json = ...) for ferdigserialiserte strenger")

suspend inline fun ApplicationCall.respondStatus(status: HttpStatusCode) {
    this.respondText("", status = status)
}

suspend inline fun ApplicationCall.respondOk() {
    this.respondText("", status = HttpStatusCode.OK)
}

suspend inline fun ApplicationCall.respondNoContent() {
    this.respondText("", status = HttpStatusCode.NoContent)
}

/**
 * Defaulter til 200 OK og Content-Type: application/json; charset=UTF-8
 */
suspend inline fun <reified T : Any> ApplicationCall.respondJson(
    statusAndValue: Pair<HttpStatusCode, T>,
) {
    respondJson(
        value = statusAndValue.second,
        status = statusAndValue.first,
    )
}

/**
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun ApplicationCall.withBehandlingId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
    crossinline onSuccess: suspend (BehandlingId) -> Unit,
) {
    parseBehandlingId(logger = logger, loggTilSikkerlogg = loggTilSikkerlogg).fold(
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

/**
 * @param logger Send inn null dersom du ikke ønsker logge.
 * @param loggTilSikkerlogg defaulter til det samme som [logger]
 */
suspend inline fun <reified T> ApplicationCall.parseBody(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, T> {
    val bodyText = this.receiveText()
    return Either.catch {
        deserialize<T>(bodyText)
    }.mapLeft {
        logger?.debug { "Feil ved deserialisering av request. Se sikkerlogg for mer kontekst." }
        if (loggTilSikkerlogg) {
            Sikkerlogg.debug(it) { "Feil ved deserialisering av request. Body: $bodyText" }
        }
        ErrorJson(
            melding = "Kunne ikke deserialisere request",
            kode = "ugyldig_request",
        )
    }
}

/**
 * @param logger dersom null logges ikke noe
 * @param loggTilSikkerlogg dersom true, logges parameterverdien til Sikkerlogg ved feil
 */
fun <T> ApplicationCall.parseParam(
    paramName: String,
    parse: (String) -> T,
    errorMessage: String,
    errorCode: String,
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, T> {
    val paramValue = this.parameters[paramName]
    if (paramValue.isNullOrBlank()) {
        logger?.debug { "Parameter $paramName mangler eller er blank. errorMessage: $errorMessage, errorCode: $errorCode" }
        return Either.Left(ErrorJson(melding = errorMessage, kode = errorCode))
    }
    return Either.catch {
        parse(paramValue)
    }.mapLeft {
        logger?.debug { "Feil ved parsing av parameter $paramName. errorMessage: $errorMessage, errorCode: $errorCode. Se sikkerlogg for mer kontekst." }
        if (loggTilSikkerlogg) {
            Sikkerlogg.debug(it) { "Feil ved parsing av parameter $paramName. paramValue: $paramValue. errorMessage: $errorMessage, errorCode: $errorCode" }
        }
        ErrorJson(melding = errorMessage, kode = errorCode)
    }
}

/**
 * @param logger dersom null logges ikke noe
 * @param loggTilSikkerlogg dersom true, logges parameterverdien til Sikkerlogg ved feil
 */
fun ApplicationCall.parseBehandlingId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, BehandlingId> = parseParam(
    paramName = "behandlingId",
    parse = BehandlingId::fromString,
    errorMessage = "Ugyldig behandling id",
    errorCode = "ugyldig_behandling_id",
    logger = logger,
    loggTilSikkerlogg = loggTilSikkerlogg,
)

/**
 * @param logger dersom null logges ikke noe
 * @param loggTilSikkerlogg dersom true, logges parameterverdien til Sikkerlogg ved feil
 */
fun ApplicationCall.parseSakId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, SakId> = parseParam(
    paramName = "sakId",
    parse = SakId::fromString,
    errorMessage = "Ugyldig sak id",
    errorCode = "ugyldig_sak_id",
    logger = logger,
    loggTilSikkerlogg = loggTilSikkerlogg,
)

/**
 * @param logger dersom null logges ikke noe
 * @param loggTilSikkerlogg dersom true, logges parameterverdien til Sikkerlogg ved feil
 */
fun ApplicationCall.parseSøknadId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, SøknadId> = parseParam(
    paramName = "søknadId",
    parse = SøknadId::fromString,
    errorMessage = "Ugyldig søknad id",
    errorCode = "ugyldig_søknad_id",
    logger = logger,
    loggTilSikkerlogg = loggTilSikkerlogg,
)

/**
 * @param logger dersom null logges ikke noe
 * @param loggTilSikkerlogg dersom true, logges parameterverdien til Sikkerlogg ved feil
 */
fun ApplicationCall.parseMeldekortId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, MeldekortId> = parseParam(
    paramName = "meldekortId",
    parse = MeldekortId::fromString,
    errorMessage = "Ugyldig meldekort id",
    errorCode = "ugyldig_meldekort_id",
    logger = logger,
    loggTilSikkerlogg = loggTilSikkerlogg,
)

/**
 * @param logger dersom null logges ikke noe
 * @param loggTilSikkerlogg dersom true, logges parameterverdien til Sikkerlogg ved feil
 */
fun ApplicationCall.parseVedtakId(
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, VedtakId> = parseParam(
    paramName = "vedtakId",
    parse = VedtakId::fromString,
    errorMessage = "Ugyldig vedtak id",
    errorCode = "ugyldig_vedtak_id",
    logger = logger,
    loggTilSikkerlogg = loggTilSikkerlogg,
)
