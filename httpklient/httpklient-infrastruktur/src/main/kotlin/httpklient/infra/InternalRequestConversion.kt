package no.nav.tiltakspenger.libs.httpklient.infra

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.httpklient.Tidsgrenser
import no.nav.tiltakspenger.libs.httpklient.UriSynlighet
import no.nav.tiltakspenger.libs.json.serialize
import java.net.URI
import java.net.http.HttpRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.toJavaDuration

internal fun HttpKlientRequest.toJavaHttpRequest(
    timeout: Duration,
    requestHeaders: Map<String, List<String>>,
    authTidsstempler: HttpKlientTidsstempler,
    uriSynlighet: UriSynlighet,
    tidsgrenser: Tidsgrenser,
): Either<HttpKlientError, PreparedHttpKlientRequest> {
    val materialisertBody = when (val requestBody = body) {
        HttpKlientRequest.Body.Ingen -> MaterialisertBody(bytes = null, visningstekst = null)

        is HttpKlientRequest.Body.Json -> Either.catch { serialize(requestBody.value) }
            .getOrElse { e ->
                return HttpKlientError.SerializationError(
                    throwable = e,
                    metadata = preFlightMetadata(
                        method = method.name,
                        uri = uri,
                        uriSynlighet = uriSynlighet,
                        tidsgrenser = tidsgrenser,
                        rawRequestString = rawRequestString(
                            requestHeaders = requestHeaders,
                            bodyAsString = "<json-serialisering feilet>",
                        ),
                        requestHeaders = requestHeaders,
                        tidsstempler = authTidsstempler,
                    ),
                ).left()
            }.let { MaterialisertBody.tekstlig(it) }

        is HttpKlientRequest.Body.FerdigJson -> MaterialisertBody.tekstlig(requestBody.json)

        is HttpKlientRequest.Body.Tekst -> MaterialisertBody.tekstlig(requestBody.tekst)

        is HttpKlientRequest.Body.Form -> MaterialisertBody.tekstlig(requestBody.enkodet)

        // Begge de binære variantene eier både sin egen enkoding og sin sikkerlogg-trygge visningstekst.
        is HttpKlientRequest.Body.Bytes -> MaterialisertBody(requestBody.bytes, requestBody.visningstekst)

        is HttpKlientRequest.Body.Multipart -> MaterialisertBody(requestBody.enkodet(), requestBody.visningstekst)
    }
    val rawRequestString = rawRequestString(
        requestHeaders = requestHeaders,
        bodyAsString = materialisertBody.visningstekst,
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
                materialisertBody.bytes?.let { HttpRequest.BodyPublishers.ofByteArray(it) } ?: HttpRequest.BodyPublishers.noBody(),
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
                method = method.name,
                uri = uri,
                uriSynlighet = uriSynlighet,
                tidsgrenser = tidsgrenser,
                rawRequestString = rawRequestString,
                requestHeaders = requestHeaders,
                tidsstempler = authTidsstempler,
            ),
        )
    }
}

/**
 * Request-bodyen ferdig materialisert: [bytes] er det som faktisk sendes; [visningstekst] er det som havner i `rawRequestString`.
 * De to har samme innhold for tekstlige bodyer, men bevisst forskjellige for binære: rå bytes skal aldri kunne havne i sikkerlogg, så de vises som en placeholder — samme regel som for binære responser ([tilLesbarResponsString]).
 */
private class MaterialisertBody(val bytes: ByteArray?, val visningstekst: String?) {
    companion object {
        /** Tekstlig body: enkodes til UTF-8-bytes med `String.toByteArray()` og sendes med `BodyPublishers.ofByteArray`, som alle andre bodyer. */
        fun tekstlig(tekst: String) = MaterialisertBody(bytes = tekst.toByteArray(), visningstekst = tekst)
    }
}

/**
 * Metadata for feil som oppstår _før_ vi har gjort et reelt HTTP-forsøk (serialization-/validerings-feil).
 * [HttpKlientMetadata] krever alle felter, så vi setter dem eksplisitt til "ikke utført": ingen response, 0 forsøk, ingen varigheter.
 * [tidsstempler] kan likevel inneholde auth-tidsstempler dersom en [no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider] rakk å bli kalt før feilen.
 */
private fun preFlightMetadata(
    method: String,
    uri: URI,
    uriSynlighet: UriSynlighet,
    tidsgrenser: Tidsgrenser,
    rawRequestString: String,
    requestHeaders: Map<String, List<String>>,
    tidsstempler: HttpKlientTidsstempler,
): HttpKlientMetadata = HttpKlientMetadata(
    method = method,
    uri = uri,
    uriSynlighet = uriSynlighet,
    tidsgrenser = tidsgrenser,
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
 * Sensitive headere maskeres (standardsettet i [maskerSensitiveHeadere] pluss konsumentens [no.nav.tiltakspenger.libs.httpklient.infra.kall.Header.sensitiv]-markerte), en [HttpKlientRequest.Body.Tekst] med `sensitiv = true` vises som `***`, og resultatet trunkeres til [MAKS_RAW_STRING_LENGDE] tegn.
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
        requestHeaders.maskerSensitiveHeadere(ekstraSensitive = sensitiveHeaderNavn).forEach { (name, values) ->
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
