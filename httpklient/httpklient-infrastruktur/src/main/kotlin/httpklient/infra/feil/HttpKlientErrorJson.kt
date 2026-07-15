package no.nav.tiltakspenger.libs.httpklient.infra.feil

import arrow.core.Either
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.json.objectMapper
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

/**
 * Parser feil-bodyen som JSON til [T] med felles objectMapper — for statuser der serveren svarer med en strukturert feil-body.
 * Returnerer `Left(DeserializationError)` med denne feilens metadata når bodyen ikke lar seg parse, slik at call sites slipper å håndbygge feiltyper.
 * Merk at [HttpKlientError.ResponsMottatt.body] alltid er lesbar tekst (aldri rå binærdata), så JSON-feilbodies kan parses trygt herfra.
 *
 * Bor i infrastruktur-modulen (i samme pakke som feilmodellen) fordi den drar json-modulen, som aldri skal inn i domenet.
 */
inline fun <reified T : Any> HttpKlientError.ResponsMottatt.bodySomJson(): Either<HttpKlientError.DeserializationError, T> {
    @Suppress("UNCHECKED_CAST")
    return bodySomJsonInternal(typeOf<T>()) as Either<HttpKlientError.DeserializationError, T>
}

@PublishedApi
internal fun HttpKlientError.ResponsMottatt.bodySomJsonInternal(type: KType): Either<HttpKlientError.DeserializationError, Any> =
    Either.catch {
        objectMapper.readValue<Any>(body, objectMapper.typeFactory.constructType(type.javaType))
    }.mapLeft { e ->
        HttpKlientError.DeserializationError(
            throwable = e,
            body = body,
            statusCode = statusCode,
            metadata = metadata,
        )
    }
