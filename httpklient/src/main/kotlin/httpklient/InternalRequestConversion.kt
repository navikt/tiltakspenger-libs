package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.tiltakspenger.libs.json.serialize
import java.net.http.HttpRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.toJavaDuration

internal fun HttpKlientRequest.toJavaHttpRequest(
    defaultTimeout: Duration,
    requestHeaders: Map<String, List<String>>,
): Either<HttpKlientError, PreparedHttpKlientRequest> {
    val bodyAsString = when (body) {
        is HttpKlientRequest.Body.Json -> Either.catch { serialize(body.value) }
            .getOrElse { e ->
                return HttpKlientError.SerializationError(
                    throwable = e,
                    metadata = preFlightMetadata(
                        rawRequestString = rawRequestString(
                            requestHeaders = requestHeaders,
                            bodyAsString = "<json-serialisering feilet>",
                        ),
                        requestHeaders = requestHeaders,
                    ),
                ).left()
            }

        is HttpKlientRequest.Body.Raw -> body.body

        is HttpKlientRequest.Body.RawJson -> body.body

        null -> null
    }
    val rawRequestString = rawRequestString(
        requestHeaders = requestHeaders,
        bodyAsString = bodyAsString,
    )

    return Either.catch {
        // Vi validerer ikke scheme selv: JDK-klienten (HttpRequest.Builder.uri) avviser scheme som ikke er http/https og lowercaser scheme først (case-insensitivt, jf. RFC 3986 §3.1).
        // Det gjenbruket gjør at vi hverken dupliserer eller divergerer fra spec-en; en ugyldig URI kaster IllegalArgumentException som fanges av Either.catch og mappes til InvalidRequest under.
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
 * Metadata for feil som oppstår _før_ vi har gjort et reelt HTTP-forsøk (serialization-/validerings-feil).
 * [HttpKlientMetadata] krever alle felter, så vi setter dem eksplisitt til "ikke utført": ingen response, 0 forsøk, ingen varigheter.
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

internal fun HttpKlientRequest.rawRequestString(
    requestHeaders: Map<String, List<String>>,
    bodyAsString: String?,
): String {
    return buildString {
        append(method.name)
        append(" ")
        append(uri)
        requestHeaders.redactSensitiveHeaders().forEach { (name, values) ->
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
