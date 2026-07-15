package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.json.objectMapper
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

/**
 * Konverterer den rå bytes-responsen etter [format] — bestemt av metoden konsumenten kalte, aldri av typeargumentet alene.
 * Den usjekkede casten er trygg by construction: de offentlige metodene på [HttpKlient] parer alltid typeargumentet med riktig [ResponsFormat].
 */
internal fun <Res> HttpKlientResponse<ByteArray>.tilTypetRespons(
    format: ResponsFormat,
): Either<HttpKlientError, HttpKlientResponse<Res>> {
    @Suppress("UNCHECKED_CAST")
    return when (format) {
        is ResponsFormat.Json -> deserializeBody(format.type) as Either<HttpKlientError, HttpKlientResponse<Res>>

        is ResponsFormat.JsonEllerNull ->
            if (statusCode in format.nullVedStatus) {
                HttpKlientResponse(
                    statusCode = statusCode,
                    body = null as Res,
                    metadata = metadata,
                ).right()
            } else {
                deserializeBody(format.type) as Either<HttpKlientError, HttpKlientResponse<Res>>
            }

        ResponsFormat.PdfBytes -> HttpKlientResponse(
            statusCode = statusCode,
            body = body as Res,
            metadata = metadata,
        ).right()

        ResponsFormat.IngenBody -> HttpKlientResponse(
            statusCode = statusCode,
            body = Unit as Res,
            metadata = metadata,
        ).right()
    }
}

/**
 * Deserialiserer respons-bytene med Jackson.
 * Jackson leser bytes direkte og detekterer encoding selv (per JSON-spesifikasjonen), så vi dekoder ikke via `String` først.
 * En tom body gis en egen feilmelding med pekepinn: statuser uten body (typisk `204`) på en ikke-nullable metode er nesten alltid et tegn på at kallet skulle brukt en EllerNull-/UtenSvar-variant.
 */
private fun HttpKlientResponse<ByteArray>.deserializeBody(
    responseType: KType,
): Either<HttpKlientError, HttpKlientResponse<Any>> {
    if (body.isEmpty()) {
        return HttpKlientError.DeserializationError(
            throwable = IllegalStateException(
                "Tom respons-body (status $statusCode) kan ikke deserialiseres til JSON. Er dette en status uten body (f.eks. 204)? Bruk getJsonEllerNull/postJsonEllerNull med nullVedStatus, eller en UtenSvar-variant.",
            ),
            body = "",
            statusCode = statusCode,
            metadata = metadata,
        ).left()
    }
    return Either.catch {
        val javaType = objectMapper.typeFactory.constructType(responseType.javaType)
        HttpKlientResponse<Any>(
            statusCode = statusCode,
            body = objectMapper.readValue<Any>(body, javaType),
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
