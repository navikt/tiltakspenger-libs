package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import java.net.URI
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.time.Duration

/**
 * Enkel fake for tester i konsumenter av `httpklient`.
 *
 * Faken implementerer [HttpKlient], tar opp alle requests som public [HttpKlientRequest]
 * snapshots, og svarer med køede handlers/responser. Hvis ingen respons er konfigurert returneres
 * en tydelig [HttpKlientError.InvalidRequest] i stedet for å gi en tilfeldig defaultverdi.
 */
class HttpKlientFake : HttpKlient {
    private val queuedResponses = mutableListOf<suspend (HttpKlientRequest) -> Either<HttpKlientError, HttpKlientResponse<Any>>>()
    private val mutableRequests = mutableListOf<HttpKlientRequest>()

    val requests: List<HttpKlientRequest> get() = mutableRequests.toList()

    fun reset() {
        queuedResponses.clear()
        mutableRequests.clear()
    }

    fun enqueueResponse(
        body: Any,
        statusCode: Int = 200,
        responseHeaders: Map<String, List<String>> = emptyMap(),
    ) {
        enqueue { request ->
            HttpKlientResponse(
                statusCode = statusCode,
                body = body,
                metadata = metadataFor(
                    request = request,
                    rawResponseString = body.toString(),
                    responseHeaders = responseHeaders,
                    statusCode = statusCode,
                    attempts = 1,
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

    fun enqueueError(error: HttpKlientError) {
        enqueue { error.left() }
    }

    fun enqueue(
        response: suspend (HttpKlientRequest) -> Either<HttpKlientError, HttpKlientResponse<Any>>,
    ) {
        queuedResponses += response
    }

    override suspend fun <Response : Any> unreifiedRequest(
        uri: URI,
        method: HttpMethod,
        responseType: KType,
        build: RequestBuilder.() -> Unit,
    ): Either<HttpKlientError, HttpKlientResponse<Response>> {
        val request = RequestBuilder(uri)
            .apply(build)
            .snapshot(responseType, method)
        mutableRequests += request

        val response = queuedResponses.removeFirstOrNull()
            ?.invoke(request)
            ?: return noResponseConfigured(request).left()

        return response.flatMap { it.castBody(responseType.classifier as KClass<*>) }
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
                body = body.toString(),
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

    private fun metadataFor(
        request: HttpKlientRequest,
        rawResponseString: String?,
        responseHeaders: Map<String, List<String>>,
        statusCode: Int?,
        attempts: Int,
    ): HttpKlientMetadata {
        return HttpKlientMetadata(
            rawRequestString = rawRequestString(request),
            rawResponseString = rawResponseString,
            requestHeaders = request.headers,
            responseHeaders = responseHeaders,
            statusCode = statusCode,
            attempts = attempts,
            attemptDurations = emptyList(),
            totalDuration = Duration.ZERO,
        )
    }

    private fun rawRequestString(request: HttpKlientRequest): String {
        return buildString {
            append(request.method.name)
            append(" ")
            append(request.uri)
            request.headers.forEach { (name, values) ->
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
        is HttpKlientRequest.Body.Json -> value.toString()
        is HttpKlientRequest.Body.Raw -> body
        is HttpKlientRequest.Body.RawJson -> body
    }
}
