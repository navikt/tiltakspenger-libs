package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.tiltakspenger.libs.json.serialize
import java.net.http.HttpRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.toJavaDuration

internal fun BuiltHttpKlientRequest.toJavaHttpRequest(
    defaultTimeout: Duration,
    requestHeaders: Map<String, List<String>>,
): Either<HttpKlientError, PreparedHttpKlientRequest> {
    val bodyAsString = when (body) {
        is RequestBody.Json -> Either.catch { serialize(body.value) }
            .getOrElse { e ->
                return HttpKlientError.SerializationError(
                    throwable = e,
                    metadata = preFlightMetadata(
                        rawRequestString = rawRequestString(
                            requestHeaders = requestHeaders,
                            bodyAsString = "<json serialization failed>",
                        ),
                        requestHeaders = requestHeaders,
                    ),
                ).left()
            }

        is RequestBody.Raw -> body.body

        is RequestBody.RawJson -> body.body

        null -> null
    }
    val rawRequestString = rawRequestString(
        requestHeaders = requestHeaders,
        bodyAsString = bodyAsString,
    )

    return Either.catch {
        require(uri.scheme == "http" || uri.scheme == "https") {
            "HttpKlientRequest.uri must use http or https scheme"
        }

        val builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout((timeout ?: defaultTimeout).toJavaDuration())

        requestHeaders.forEach { (name, values) -> values.forEach { value -> builder.header(name, value) } }

        builder
            .method(
                method.name,
                bodyAsString?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody(),
            )
            .build()
            .let {
                PreparedHttpKlientRequest(
                    request = it,
                    rawRequestString = rawRequestString,
                )
            }
    }.mapLeft { e ->
        HttpKlientError.InvalidRequest(
            throwable = e,
            metadata = preFlightMetadata(
                rawRequestString = rawRequestString,
                requestHeaders = requestHeaders,
            ),
        )
    }
}

/**
 * Metadata for feil som oppstår _før_ vi har gjort et reelt HTTP-forsøk
 * (serialization-/validerings-feil). [HttpKlientMetadata] krever alle felter, så vi setter dem
 * eksplisitt til "ikke utført": ingen response, 0 forsøk, ingen varigheter.
 */
private fun preFlightMetadata(
    rawRequestString: String,
    requestHeaders: Map<String, List<String>>,
): HttpKlientMetadata = HttpKlientMetadata(
    rawRequestString = rawRequestString,
    rawResponseString = null,
    requestHeaders = requestHeaders,
    responseHeaders = emptyMap(),
    statusCode = null,
    attempts = 0,
    attemptDurations = emptyList(),
    totalDuration = ZERO,
)

internal fun BuiltHttpKlientRequest.rawRequestString(
    requestHeaders: Map<String, List<String>>,
    bodyAsString: String?,
): String {
    return buildString {
        append(method.name)
        append(" ")
        append(uri)
        requestHeaders.forEach { (name, values) ->
            values.forEach { value ->
                append("\n")
                append(name)
                append(": ")
                append(value)
            }
        }
        if (bodyAsString != null) {
            append("\n\n")
            append(bodyAsString)
        }
    }
}
