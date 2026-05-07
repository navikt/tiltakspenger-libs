package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.AccessToken
import java.net.URI
import java.time.Clock
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface HttpKlient {
    /**
     * Public because of limitations in the language, use the reified extension functions.
     *
     * Tar [responseType] som [KType] (ikke `KClass`) slik at generiske parametre overlever
     * gjennom Jackson-deserialiseringen — `request<List<Foo>>(...)` produserer faktisk
     * `List<Foo>`, ikke `List<LinkedHashMap>`. Reified extensions bruker [typeOf] for å
     * fange typen.
     */
    suspend fun <Response : Any> unreifiedRequest(
        uri: URI,
        method: HttpMethod,
        responseType: KType,
        build: RequestBuilder.() -> Unit,
    ): Either<HttpKlientError, HttpKlientResponse<Response>>

    companion object {
        /**
         * Eneste public måte å lage en [HttpKlient] på.
         * Faktisk implementasjon ([JavaHttpKlient]) er intern slik at konsumenter ikke binder seg til den konkrete typen.
         *
         * [clock] er påkrevd for å sikre at all tidsmåling går via samme klokke (se AGENTS.md).
         */
        operator fun invoke(
            clock: Clock,
            config: HttpKlientConfig.() -> Unit = {},
        ): HttpKlient = JavaHttpKlient(HttpKlientConfig(clock).apply(config))
    }

    /**
     * Konfigurasjon av en [HttpKlient]. Alle felter har defaultverdier som gjør at klienten
     * fungerer "out of the box" uten resilience, auth eller logging. Per-request overstyringer
     * via [RequestBuilder] tar alltid presedens over disse defaultene.
     */
    class HttpKlientConfig(
        /**
         * Klokken som brukes til all tidsmåling i klienten (retry-timing, metadata, auth-token-utløp).
         * Påkrevd; ingen default i produksjonskode (se AGENTS.md).
         */
        val clock: Clock,
    ) {
        /**
         * Connect-timeout for den underliggende `java.net.http.HttpClient`. Brukes kun ved opprettelse
         * av selve [java.net.http.HttpClient]-instansen.
         */
        var connectTimeout: Duration = 10.seconds

        /**
         * Default per-request timeout. Kan overstyres per request via [RequestBuilder.timeout].
         */
        var defaultTimeout: Duration = 30.seconds

        /**
         * Default predikat for hvilke HTTP-statuser som regnes som suksess. Kan overstyres per
         * request via [RequestBuilder.successStatus].
         */
        var successStatus: (Int) -> Boolean = HttpStatusSuccess.is2xx

        /**
         * Default logging-config. [HttpKlientLoggingConfig.Disabled] betyr ingen logging. Kan
         * overstyres per request via [RequestBuilder.logging] / [RequestBuilder.disableLogging].
         */
        var logging: HttpKlientLoggingConfig = HttpKlientLoggingConfig.Disabled

        /**
         * Default retry-config. [RetryConfig.None] betyr ingen retries. Kan overstyres per request
         * via [RequestBuilder.retryConfig].
         */
        var defaultRetry: RetryConfig = RetryConfig.None

        /**
         * Default circuit breaker-config. [CircuitBreakerConfig.None] betyr ingen circuit breaker.
         * Kan overstyres per request via [RequestBuilder.circuitBreakerConfig]. State er lokal til
         * den enkelte [HttpKlient]-instansen.
         */
        var defaultCircuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig.None

        /**
         * Valgfri token-provider. Når satt vil klienten kalle denne foran hver request og legge
         * resultatet i `Authorization: Bearer <token>`-headeren (med mindre konsumenten allerede
         * har satt `Authorization` eksplisitt, eller har satt en per-request token via
         * [RequestBuilder.bearerToken]). Hvis providern kaster, returneres
         * [HttpKlientError.AuthError] og det HTTP-kallet gjøres aldri.
         */
        var authTokenProvider: (suspend () -> AccessToken)? = null
    }
}

suspend inline fun <reified Response : Any> HttpKlient.request(
    uri: URI,
    method: HttpMethod,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return unreifiedRequest(uri, method, typeOf<Response>()) {
        build.invoke(this)
    }
}

suspend inline fun <reified Response : Any> HttpKlient.get(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return unreifiedRequest(uri, HttpMethod.GET, typeOf<Response>()) {
        build.invoke(this)
    }
}

suspend inline fun <reified Response : Any> HttpKlient.post(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return unreifiedRequest(uri, HttpMethod.POST, typeOf<Response>()) {
        build.invoke(this)
    }
}

suspend inline fun <reified Response : Any> HttpKlient.put(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return unreifiedRequest(uri, HttpMethod.PUT, typeOf<Response>()) {
        build.invoke(this)
    }
}

suspend inline fun <reified Response : Any> HttpKlient.patch(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return unreifiedRequest(uri, HttpMethod.PATCH, typeOf<Response>()) {
        build.invoke(this)
    }
}

suspend inline fun <reified Response : Any> HttpKlient.delete(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return unreifiedRequest(uri, HttpMethod.DELETE, typeOf<Response>()) {
        build.invoke(this)
    }
}

suspend inline fun <reified Response : Any> HttpKlient.head(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return unreifiedRequest(uri, HttpMethod.HEAD, typeOf<Response>()) {
        build.invoke(this)
    }
}

suspend inline fun <reified Response : Any> HttpKlient.options(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    return unreifiedRequest(uri, HttpMethod.OPTIONS, typeOf<Response>()) {
        build.invoke(this)
    }
}

/**
 * Shorthand for det vanligste POST-tilfellet: send [body] som JSON. Tilsvarer
 * `post<R>(uri) { json(body); build() }`.
 *
 * Merk overload-resolution på `RequestBuilder.json`: en [String] dispatcher til `json(body: String)`
 * og sendes som *allerede serialisert* JSON, mens andre typer serialiseres via
 * `tiltakspenger-libs/json`. Hvis du trenger å sende rå tekst (ikke-JSON) eller en spesifikk
 * `Content-Type`, bruk lambda-formen [post] med [RequestBuilder.body] / [RequestBuilder.header].
 */
suspend inline fun <reified Response : Any> HttpKlient.post(
    uri: URI,
    body: Any,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> = post(uri) {
    json(body)
    build.invoke(this)
}

/**
 * Shorthand for det vanligste PUT-tilfellet: send [body] som JSON. Se [post] for detaljer rundt
 * String-overload og når lambda-formen skal foretrekkes.
 */
suspend inline fun <reified Response : Any> HttpKlient.put(
    uri: URI,
    body: Any,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> = put(uri) {
    json(body)
    build.invoke(this)
}

/**
 * Shorthand for det vanligste PATCH-tilfellet: send [body] som JSON. Se [post] for detaljer rundt
 * String-overload og når lambda-formen skal foretrekkes.
 */
suspend inline fun <reified Response : Any> HttpKlient.patch(
    uri: URI,
    body: Any,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> = patch(uri) {
    json(body)
    build.invoke(this)
}
