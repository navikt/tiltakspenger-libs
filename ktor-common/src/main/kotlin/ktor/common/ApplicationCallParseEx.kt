package no.nav.tiltakspenger.libs.ktor.common

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import no.nav.tiltakspenger.libs.common.BehandlingId
import no.nav.tiltakspenger.libs.common.MeldekortId
import no.nav.tiltakspenger.libs.common.SakId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.VedtakId
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

// Må være public pga. inline
val DEFAULT_APPLICATION_CALL_EX_LOGGER: KLogger by lazy { KotlinLogging.logger {} }

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
