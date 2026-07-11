package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.json.objectMapper
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

/**
 * Deserialiserer respons-bytene til [Response] med Jackson.
 * Jackson leser bytes direkte og detekterer encoding selv (per JSON-spesifikasjonen), så vi dekoder ikke via `String` først.
 */
internal fun <Response : Any> HttpKlientResponse<ByteArray>.deserializeBody(
    responseType: KType,
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return Either.catch {
        val javaType = objectMapper.typeFactory.constructType(responseType.javaType)
        @Suppress("UNCHECKED_CAST")
        HttpKlientResponse(
            statusCode = statusCode,
            body = objectMapper.readValue<Any>(body, javaType) as Response,
            metadata = metadata,
        )
    }.mapLeft { e ->
        HttpKlientError.DeserializationError(
            throwable = e,
            // body skal være lesbar tekst (den havner typisk i konsumentens sikkerlogg), aldri rå binærdata — samme regel som metadata.rawResponseString.
            body = body.tilLesbarResponsString(metadata.responseHeaders),
            statusCode = statusCode,
            metadata = metadata,
        )
    }
}

/**
 * Konverterer den rå bytes-responsen til forventet [responseType]:
 * - `ByteArray` får de rå bytene uendret (binært innhold, f.eks. PDF).
 * - `String` dekodes med charset fra `Content-Type` (default UTF-8) — samme oppførsel som JDK-ens `BodyHandlers.ofString()`.
 * - `Unit` ignorerer bodyen.
 * - Alt annet deserialiseres med Jackson via [deserializeBody].
 */
internal fun <Response : Any> HttpKlientResponse<ByteArray>.toTypedResponse(
    responseType: KType,
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return when (responseType.classifier) {
        ByteArray::class -> {
            @Suppress("UNCHECKED_CAST")
            HttpKlientResponse(
                statusCode = statusCode,
                body = body as Response,
                metadata = metadata,
            ).right()
        }

        String::class -> {
            @Suppress("UNCHECKED_CAST")
            HttpKlientResponse(
                statusCode = statusCode,
                body = body.dekodSomTekst(metadata.responseHeaders) as Response,
                metadata = metadata,
            ).right()
        }

        Unit::class -> {
            @Suppress("UNCHECKED_CAST")
            HttpKlientResponse(
                statusCode = statusCode,
                body = Unit as Response,
                metadata = metadata,
            ).right()
        }

        else -> deserializeBody(responseType)
    }
}
