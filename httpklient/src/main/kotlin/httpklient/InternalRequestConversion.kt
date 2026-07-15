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
    timeout: Duration,
    requestHeaders: Map<String, List<String>>,
    authTidsstempler: HttpKlientTidsstempler,
): Either<HttpKlientError, PreparedHttpKlientRequest> {
    val bodyAsString = when (val requestBody = body) {
        HttpKlientRequest.Body.Ingen -> null

        is HttpKlientRequest.Body.Json -> Either.catch { serialize(requestBody.value) }
            .getOrElse { e ->
                return HttpKlientError.SerializationError(
                    throwable = e,
                    metadata = preFlightMetadata(
                        rawRequestString = rawRequestString(
                            requestHeaders = requestHeaders,
                            bodyAsString = "<json-serialisering feilet>",
                        ),
                        requestHeaders = requestHeaders,
                        tidsstempler = authTidsstempler,
                    ),
                ).left()
            }

        is HttpKlientRequest.Body.FerdigJson -> requestBody.json

        is HttpKlientRequest.Body.Tekst -> requestBody.tekst

        is HttpKlientRequest.Body.Form -> requestBody.enkodet
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
            .timeout(timeout.toJavaDuration())

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
                tidsstempler = authTidsstempler,
            ),
        )
    }
}

/**
 * Metadata for feil som oppstår _før_ vi har gjort et reelt HTTP-forsøk (serialization-/validerings-feil).
 * [HttpKlientMetadata] krever alle felter, så vi setter dem eksplisitt til "ikke utført": ingen response, 0 forsøk, ingen varigheter.
 * [tidsstempler] kan likevel inneholde auth-tidsstempler dersom en [AuthTokenProvider] rakk å bli kalt før feilen.
 */
private fun preFlightMetadata(
    rawRequestString: String,
    requestHeaders: Map<String, List<String>>,
    tidsstempler: HttpKlientTidsstempler,
): HttpKlientMetadata = HttpKlientMetadata(
    rawRequestString = rawRequestString,
    rawResponseString = null,
    requestHeaders = requestHeaders,
    responseHeaders = emptyMap(),
    statusCode = null,
    attempts = 0,
    attemptDurations = emptyList(),
    totalDuration = ZERO,
    tidsstempler = tidsstempler,
)

/**
 * Lesbar tekst-representasjon av requesten, til [HttpKlientMetadata.rawRequestString].
 * Sensitive headere maskeres (standardsettet i [redactSensitiveHeaders] pluss konsumentens [Header.sensitiv]-markerte), en [HttpKlientRequest.Body.Tekst] med `sensitiv = true` vises som `***`, og resultatet trunkeres til [MAKS_RAW_STRING_LENGDE] tegn.
 * Selve HTTP-requesten sendes alltid med ekte verdier; dette gjelder kun tekstrepresentasjonen som havner i logger.
 */
internal fun HttpKlientRequest.rawRequestString(
    requestHeaders: Map<String, List<String>>,
    bodyAsString: String?,
): String {
    val visningsBody = when (val requestBody = body) {
        is HttpKlientRequest.Body.Tekst -> if (requestBody.sensitiv) "***" else bodyAsString
        else -> bodyAsString
    }
    return buildString {
        append(method.name)
        append(" ")
        append(uri)
        requestHeaders.redactSensitiveHeaders(ekstraSensitive = sensitiveHeaderNavn).forEach { (name, values) ->
            values.forEach { value ->
                append("\n")
                append(name)
                append(": ")
                append(value)
            }
        }
        if (visningsBody != null) {
            append("\n\n")
            append(visningsBody)
        }
    }.trunkert()
}
