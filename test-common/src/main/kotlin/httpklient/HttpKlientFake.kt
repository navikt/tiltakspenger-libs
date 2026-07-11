package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.fail
import no.nav.tiltakspenger.libs.json.serialize
import java.io.IOException
import java.net.http.HttpTimeoutException
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.time.Duration

/**
 * Enkel fake for tester i konsumenter av `httpklient`.
 *
 * Faken implementerer [HttpKlient], tar opp alle innkommende [HttpKlientRequest] for assertion, og svarer med køede handlers/responser.
 * Hvis ingen respons er konfigurert returneres en tydelig [HttpKlientError.InvalidRequest] i stedet for å gi en tilfeldig defaultverdi.
 *
 * Trådsikker: all tilgang til den interne kø-/request-tilstanden er synkronisert på [lock], slik at faken trygt kan deles mellom coroutines/tråder i parallelle tester (jf. at den virkelige [HttpKlient] også kan brukes parallelt).
 * Den køede handleren kjøres bevisst _utenfor_ låsen siden den er `suspend`; selve opptaket av requesten og uttaket av handleren skjer atomisk under låsen.
 */
class HttpKlientFake : HttpKlient {
    private val lock = Any()
    private val queuedResponses = mutableListOf<suspend (HttpKlientRequest) -> Either<HttpKlientError, HttpKlientResponse<Any>>>()
    private val mutableRequests = mutableListOf<HttpKlientRequest>()

    val requests: List<HttpKlientRequest> get() = synchronized(lock) { mutableRequests.toList() }

    fun reset() {
        synchronized(lock) {
            queuedResponses.clear()
            mutableRequests.clear()
        }
    }

    fun enqueueResponse(
        body: Any,
        statusCode: Int = 200,
        responseHeaders: Map<String, List<String>> = emptyMap(),
        rawResponseString: String? = defaultRawResponseString(body),
        attempts: Int = 1,
        attemptDurations: List<Duration> = List(attempts) { Duration.ZERO },
        /** Total tid inkl. backoff mellom forsøk; null gir summen av attemptDurations. Sett eksplisitt for å modellere retry-backoff. */
        totalDuration: Duration? = null,
    ) {
        krevUtførtForsøk(attempts)
        require(attemptDurations.size == attempts) {
            "attemptDurations.size (${attemptDurations.size}) må være lik attempts ($attempts), ellers blir timing-metadata inkonsistent"
        }
        krevKonsistentTotalDuration(totalDuration, attemptDurations)
        enqueue { request ->
            HttpKlientResponse(
                statusCode = statusCode,
                body = body,
                metadata = metadataFor(
                    request = request,
                    rawResponseString = rawResponseString,
                    responseHeaders = responseHeaders,
                    statusCode = statusCode,
                    attempts = attempts,
                    attemptDurations = attemptDurations,
                    totalDuration = totalDuration,
                ),
            ).right()
        }
    }

    fun enqueueUnitResponse(
        statusCode: Int = 204,
        responseHeaders: Map<String, List<String>> = emptyMap(),
    ) {
        enqueueResponse(
            body = Unit,
            statusCode = statusCode,
            responseHeaders = responseHeaders,
        )
    }

    fun enqueueStringResponse(
        body: String,
        statusCode: Int = 200,
        responseHeaders: Map<String, List<String>> = emptyMap(),
    ) {
        enqueueResponse(
            body = body,
            statusCode = statusCode,
            responseHeaders = responseHeaders,
        )
    }

    /**
     * Køer en binær respons (f.eks. en PDF) for requester med `ByteArray` som response-type.
     * `rawResponseString` i metadata defaulter til samme placeholder som produksjon (`<binær respons, N bytes>`) — sikkerlogg skal aldri få rå binærdata.
     */
    fun enqueueBytesResponse(
        body: ByteArray,
        statusCode: Int = 200,
        responseHeaders: Map<String, List<String>> = emptyMap(),
    ) {
        enqueueResponse(
            body = body,
            statusCode = statusCode,
            responseHeaders = responseHeaders,
        )
    }

    fun enqueueError(error: HttpKlientError) {
        enqueue { error.left() }
    }

    /**
     * Køer en [HttpKlientError.Timeout] med korrekt utfylt metadata (effektive headere, redaksjon, timing).
     * Bruk denne i stedet for [enqueueError] når du vil teste timeout-håndtering uten å bygge metadata selv.
     */
    fun enqueueTimeout(
        throwable: Throwable = HttpTimeoutException("HttpKlientFake simulert timeout"),
        attempts: Int = 1,
    ) {
        krevUtførtForsøk(attempts)
        enqueue { request ->
            HttpKlientError.Timeout(
                throwable = throwable,
                metadata = metadataFor(request, rawResponseString = null, responseHeaders = emptyMap(), statusCode = null, attempts = attempts),
            ).left()
        }
    }

    /**
     * Køer en [HttpKlientError.NetworkError] med korrekt utfylt metadata.
     * Modellerer at requesten aldri nådde fram (DNS, connection refused, tilkobling brutt e.l.).
     */
    fun enqueueNetworkError(
        throwable: Throwable = IOException("HttpKlientFake simulert nettverksfeil"),
        attempts: Int = 1,
    ) {
        krevUtførtForsøk(attempts)
        enqueue { request ->
            HttpKlientError.NetworkError(
                throwable = throwable,
                metadata = metadataFor(request, rawResponseString = null, responseHeaders = emptyMap(), statusCode = null, attempts = attempts),
            ).left()
        }
    }

    /**
     * Køer en [HttpKlientError.UventetStatus] med korrekt utfylt metadata.
     * Modellerer en respons som ble mottatt, men hvis status ikke regnes som vellykket av klienten.
     */
    fun enqueueUventetStatus(
        statusCode: Int,
        body: String = "",
        responseHeaders: Map<String, List<String>> = emptyMap(),
        attempts: Int = 1,
    ) {
        krevUtførtForsøk(attempts)
        enqueue { request ->
            HttpKlientError.UventetStatus(
                statusCode = statusCode,
                body = body,
                metadata = metadataFor(request, rawResponseString = body, responseHeaders = responseHeaders, statusCode = statusCode, attempts = attempts),
            ).left()
        }
    }

    /**
     * Køer en [HttpKlientError.DeserializationError] med korrekt utfylt metadata.
     * Modellerer en respons som ble mottatt med vellykket status, men hvor body-en ikke lot seg tolke til forventet type.
     */
    fun enqueueDeserializationError(
        statusCode: Int = 200,
        body: String = "",
        responseHeaders: Map<String, List<String>> = emptyMap(),
        throwable: Throwable = IllegalStateException("HttpKlientFake simulert deserialiseringsfeil"),
        attempts: Int = 1,
    ) {
        krevUtførtForsøk(attempts)
        enqueue { request ->
            HttpKlientError.DeserializationError(
                throwable = throwable,
                body = body,
                statusCode = statusCode,
                metadata = metadataFor(request, rawResponseString = body, responseHeaders = responseHeaders, statusCode = statusCode, attempts = attempts),
            ).left()
        }
    }

    fun enqueue(
        response: suspend (HttpKlientRequest) -> Either<HttpKlientError, HttpKlientResponse<Any>>,
    ) {
        synchronized(lock) { queuedResponses += response }
    }

    override suspend fun <Response : Any> request(
        request: HttpKlientRequest,
        responseType: KType,
    ): Either<HttpKlientError, HttpKlientResponse<Response>> {
        // Opptak av request + uttak av handler skjer atomisk under låsen. Selve handleren kjøres
        // utenfor låsen siden den er `suspend` (vi kan ikke holde en monitor-lås over en suspensjon).
        val handler = synchronized(lock) {
            mutableRequests += request
            queuedResponses.removeFirstOrNull()
        }

        val response = handler
            ?.invoke(request)
            ?: return noResponseConfigured(request).left()

        // [KType.classifier] er ikke garantert å være en [KClass] (f.eks. type-parametre eller ukjente classifiers).
        // En usikker cast ville kastet ClassCastException og krasjet faken i stedet for å returnere en kontrollert Either.Left.
        val responseClass = responseType.classifier as? KClass<*>
            ?: return invalidResponseType(request, responseType).left()

        return response.flatMap { it.castBody(responseClass) }
    }

    private fun invalidResponseType(
        request: HttpKlientRequest,
        responseType: KType,
    ): HttpKlientError.InvalidRequest {
        return HttpKlientError.InvalidRequest(
            throwable = IllegalArgumentException(
                "HttpKlientFake støtter kun responstyper med en KClass-classifier, men fikk $responseType",
            ),
            metadata = metadataFor(
                request = request,
                rawResponseString = null,
                responseHeaders = emptyMap(),
                statusCode = null,
                attempts = 0,
            ),
        )
    }

    private fun noResponseConfigured(request: HttpKlientRequest): HttpKlientError.InvalidRequest {
        return HttpKlientError.InvalidRequest(
            throwable = IllegalStateException(
                "HttpKlientFake mangler konfigurert respons for ${request.method} ${request.uri}",
            ),
            metadata = metadataFor(
                request = request,
                rawResponseString = null,
                responseHeaders = emptyMap(),
                statusCode = null,
                attempts = 0,
            ),
        )
    }

    private fun <Response : Any> HttpKlientResponse<Any>.castBody(
        responseType: KClass<*>,
    ): Either<HttpKlientError, HttpKlientResponse<Response>> {
        if (!responseType.javaObjectType.isInstance(body)) {
            return HttpKlientError.DeserializationError(
                throwable = IllegalStateException(
                    "HttpKlientFake konfigurert med body av type ${body::class.qualifiedName}, " +
                        "men requesten forventet ${responseType.qualifiedName}",
                ),
                // I produksjon kommer body alltid fra den lesbare respons-stringen, så vi speiler metadata.rawResponseString (med samme default som enqueueResponse som fallback) for å holde error.body konsistent med metadata.
                body = metadata.rawResponseString ?: defaultRawResponseString(body),
                statusCode = statusCode,
                metadata = metadata,
            ).left()
        }
        @Suppress("UNCHECKED_CAST")
        return HttpKlientResponse(
            statusCode = statusCode,
            body = body as Response,
            metadata = metadata,
        ).right()
    }

    /**
     * Speiler produksjonens regler for `rawResponseString`: tom streng for `Unit` (ingen body), placeholder for binært innhold, ellers `toString()`.
     * Placeholder-formatet holdes med vilje i synk med `binærResponsPlaceholder` (som er `private` i `httpklient`), slik at fakens metadata ser ut som produksjonens — og sikkerlogg aldri får rå binærdata.
     */
    private fun defaultRawResponseString(body: Any): String = when (body) {
        is Unit -> ""
        is ByteArray -> "<binær respons, ${body.size} bytes>"
        else -> body.toString()
    }

    /**
     * Validerer at en enqueue-helper som modellerer et faktisk utført forsøk (mottatt respons eller forsøk som feilet) har minst ett forsøk.
     * `attempts = 0` er forbeholdt de interne stiene der requesten aldri ble forsøkt (f.eks. [noResponseConfigured]).
     */
    private fun krevUtførtForsøk(attempts: Int) {
        require(attempts >= 1) { "attempts må være minst 1 for en utført request, var $attempts" }
    }

    /**
     * Validerer at en eksplisitt [totalDuration] er minst summen av [attemptDurations].
     * Speiler produksjon, der totalDuration er forsøkstid pluss ikke-negativ backoff og derfor aldri er mindre enn summen.
     */
    private fun krevKonsistentTotalDuration(totalDuration: Duration?, attemptDurations: List<Duration>) {
        if (totalDuration == null) return
        val summertForsøkstid = attemptDurations.fold(Duration.ZERO) { sum, duration -> sum + duration }
        require(totalDuration >= summertForsøkstid) {
            "totalDuration ($totalDuration) kan ikke være mindre enn summen av attemptDurations ($summertForsøkstid)"
        }
    }

    private fun metadataFor(
        request: HttpKlientRequest,
        rawResponseString: String?,
        responseHeaders: Map<String, List<String>>,
        statusCode: Int?,
        attempts: Int,
        attemptDurations: List<Duration> = List(attempts) { Duration.ZERO },
        /** I produksjon inkluderer totalDuration backoff mellom forsøk og kan overstige summen av attemptDurations; null gir summen som fornuftig standard. */
        totalDuration: Duration? = null,
        /**
         * Fakens standard er [HttpKlientTidsstempler.INGEN]: faken har ingen klokke og kaller ingen ekte auth-provider, så den kan ikke produsere meningsfulle absolutte tidsstempler.
         * Tester som trenger å verifisere tidsstempler kan sende dem inn eksplisitt.
         */
        tidsstempler: HttpKlientTidsstempler = HttpKlientTidsstempler.INGEN,
    ): HttpKlientMetadata {
        val effectiveHeaders = effectiveRequestHeaders(request)
        val summertForsøkstid = attemptDurations.fold(Duration.ZERO) { sum, duration -> sum + duration }
        return HttpKlientMetadata(
            rawRequestString = rawRequestString(request, effectiveHeaders),
            rawResponseString = rawResponseString,
            requestHeaders = effectiveHeaders,
            responseHeaders = responseHeaders,
            statusCode = statusCode,
            attempts = attempts,
            attemptDurations = attemptDurations,
            totalDuration = totalDuration ?: summertForsøkstid,
            tidsstempler = tidsstempler,
        )
    }

    /**
     * Speiler [JavaHttpKlient]: en per-request bearer-token satt via [RequestBuilder.bearerToken]
     * materialiseres til `Authorization: Bearer <token>` før requesten "sendes", og det er disse
     * effektive headerne som havner i [HttpKlientMetadata.requestHeaders].
     * Settes kun hvis `Authorization` ikke allerede er satt (case-insensitivt), akkurat som produksjon.
     * Holdes med vilje i synk med `InternalHeaders.withBearerToken` (som er `internal` i `httpklient`).
     */
    private fun effectiveRequestHeaders(request: HttpKlientRequest): Map<String, List<String>> {
        val token = request.authToken ?: return request.headers
        if (request.headers.keys.any { it.equals("Authorization", ignoreCase = true) }) return request.headers
        return buildMap(request.headers.size + 1) {
            putAll(request.headers)
            put("Authorization", listOf("Bearer ${token.token}"))
        }
    }

    private fun rawRequestString(
        request: HttpKlientRequest,
        effectiveHeaders: Map<String, List<String>>,
    ): String {
        return buildString {
            append(request.method.name)
            append(" ")
            append(request.uri)
            effectiveHeaders.redactSensitiveHeaders().forEach { (name, values) ->
                values.forEach { value ->
                    append("\n")
                    append(name)
                    append(": ")
                    append(value)
                }
            }
            request.body?.rawString()?.let { body ->
                append("\n\n")
                append(body)
            }
        }
    }

    private fun HttpKlientRequest.Body.rawString(): String = when (this) {
        // Speiler produksjon: en Json-body serialiseres med samme `serialize` som JavaHttpKlient, ikke `value.toString()`.
        // `serialize` kan kaste (f.eks. selvrefererende DTO / banlist).
        // I en fake er det nesten alltid en feil i testoppsettet, så vi feiler testen høylytt med kotest `fail` i stedet for å skjule det bak en placeholder.
        is HttpKlientRequest.Body.Json -> Either.catch { serialize(value) }.getOrElse { e ->
            fail("HttpKlientFake klarte ikke å serialisere JSON-body av type ${value::class.qualifiedName}: ${e.message}")
        }

        is HttpKlientRequest.Body.Raw -> body

        is HttpKlientRequest.Body.RawJson -> body
    }
}
