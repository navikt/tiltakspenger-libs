package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.json.objectMapper
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

internal fun <Response : Any> HttpKlientResponse<String>.deserializeBody(
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
            body = body,
            statusCode = statusCode,
            metadata = metadata,
        )
    }
}

internal fun <Response : Any> HttpKlientResponse<String>.toTypedResponse(
    responseType: KType,
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return when (responseType.classifier) {
        String::class -> {
            @Suppress("UNCHECKED_CAST")
            HttpKlientResponse(
                statusCode = statusCode,
                body = body as Response,
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

internal fun BuiltHttpKlientRequest.withDefaultJsonAcceptHeaderForResponse(
    responseType: KType,
): BuiltHttpKlientRequest {
    return when (responseType.classifier) {
        String::class, Unit::class -> this
        else -> copy(headers = headers.withDefaultJsonAcceptHeader())
    }
}

/** Henter ut top-level [KClass] fra en [KType]. For `List<Foo>` returneres `List::class`. */
internal val KType.rawClass: KClass<*>
    get() = classifier as? KClass<*>
        ?: error("Forventet en KClass-classifier for KType $this, fikk ${classifier?.javaClass}")
