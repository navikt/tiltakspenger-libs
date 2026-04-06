package no.nav.tiltakspenger.libs.ktor.common

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.application.ApplicationCall
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

/**
 * @param errorMessage denne svares ut og logges - ikke send inn noe personsensitivt
 * @param errorCode denne svares ut og logges - ikke send inn noe personsensitivt
 * @param logger dersom null logges ikke noe
 * @param loggTilSikkerlogg dersom true, logges parameterverdien til Sikkerlogg ved feil
 */
fun <T> ApplicationCall.parseQueryParam(
    paramName: String,
    parse: (String) -> T,
    errorMessage: String,
    errorCode: String,
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, T> {
    val paramValue = this.request.queryParameters[paramName]
    if (paramValue.isNullOrBlank()) {
        logger?.debug { "Query parameter $paramName mangler eller er blank. errorMessage: $errorMessage, errorCode: $errorCode" }
        return Either.Left(ErrorJson(melding = errorMessage, kode = errorCode))
    }
    return Either.catch {
        parse(paramValue)
    }.mapLeft {
        logger?.debug { "Feil ved parsing av query parameter $paramName. errorMessage: $errorMessage, errorCode: $errorCode. Se sikkerlogg for mer kontekst." }
        if (loggTilSikkerlogg) {
            Sikkerlogg.debug(it) { "Feil ved parsing av query parameter $paramName. paramValue: $paramValue. errorMessage: $errorMessage, errorCode: $errorCode" }
        }
        ErrorJson(melding = errorMessage, kode = errorCode)
    }
}

/**
 * @return null dersom parameteren ikke finnes eller er blank.
 * @param errorMessage denne svares ut og logges - ikke send inn noe personsensitivt
 * @param errorCode denne svares ut og logges - ikke send inn noe personsensitivt
 * @param logger dersom null logges ikke noe
 * @param loggTilSikkerlogg dersom true, logges parameterverdien til Sikkerlogg ved feil
 */
fun <T> ApplicationCall.parseOptionalQueryParam(
    paramName: String,
    parse: (String) -> T,
    errorMessage: String,
    errorCode: String,
    logger: KLogger? = DEFAULT_APPLICATION_CALL_EX_LOGGER,
    loggTilSikkerlogg: Boolean = logger != null,
): Either<ErrorJson, T?> {
    val paramValue = this.request.queryParameters[paramName]
    if (paramValue.isNullOrBlank()) {
        return Either.Right(null)
    }
    return Either.catch {
        parse(paramValue)
    }.mapLeft {
        logger?.debug { "Feil ved parsing av optional query parameter $paramName. errorMessage: $errorMessage, errorCode: $errorCode. Se sikkerlogg for mer kontekst." }
        if (loggTilSikkerlogg) {
            Sikkerlogg.debug(it) { "Feil ved parsing av optional query parameter $paramName. paramValue: $paramValue. errorMessage: $errorMessage, errorCode: $errorCode" }
        }
        ErrorJson(melding = errorMessage, kode = errorCode)
    }
}
