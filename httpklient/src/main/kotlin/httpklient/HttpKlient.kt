package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig
import java.net.URI
import java.net.http.HttpClient
import java.time.Clock
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Leverandør av auth-token til [HttpKlient].
 *
 * Bevisst et vanlig interface (ikke en typealias eller `fun interface`): eksisterende wiring må da implementere [hentToken] eksplisitt og navngi [skipCache]-parameteren, i stedet for at en gammel parameterløs lambda kompilerer videre med en implisitt, ignorert `it`.
 * Det tvinger konsumenten til å ta stilling til parameteren når `libs` bumpes, slik at skip-cache-retryen faktisk videreformidler et ferskt token og ikke stille blir en noop (samme felle som den gamle `invaliderCache`-noopen).
 *
 * [hentToken] kalles med `skipCache = false` på det første forsøket for en request.
 * Hvis serveren svarer med en av [HttpKlient.HttpKlientConfig.skipCacheRetryStatuses] (default `401`), gjør klienten ett nytt forsøk der [hentToken] kalles med `skipCache = true`, slik at et cachet token som ble avvist kan byttes ut med et ferskt.
 * Implementasjonen bestemmer selv hva [skipCache] betyr (typisk å tvinge fornyelse forbi en lokal token-cache, f.eks. `skip_cache` mot NAIS Texas).
 */
interface AuthTokenProvider {
    suspend fun hentToken(skipCache: Boolean): AccessToken
}

/**
 * Felles HTTP-klient for tiltakspenger-tjenester, med innebygd auth, timeouts, retry, circuit breaker, logging og en enhetlig feiltype ([HttpKlientError]).
 *
 * ## Mangler noe felles?
 * Trenger du oppførsel som andre konsumenter også vil ha nytte av (nye config-felter, hjelpere rundt [HttpKlientError], nye test-utilities på `HttpKlientFake` e.l.), legg det heller til her i libs enn å bygge det lokalt i din egen app.
 * Poenget med modulen er at klientene konvergerer mot samme oppførsel i stedet for at hver app dupliserer sin egen variant.
 */
interface HttpKlient {
    /**
     * Den eneste metoden en [HttpKlient]-implementasjon må implementere.
     *
     * Tar en ferdig materialisert [HttpKlientRequest] (bygget av de reified verb-extensionene) i stedet for en builder-callback, slik at implementasjoner får rene data og fakes kan asserte direkte på requesten.
     * Tar [responseType] som [KType] (ikke `KClass`) slik at generiske parametre overlever gjennom Jackson-deserialiseringen — `request<List<Foo>>(...)` produserer faktisk `List<Foo>`, ikke `List<LinkedHashMap>`.
     * De reified extensionene bruker [typeOf] for å fange typen.
     */
    suspend fun <Response : Any> request(
        request: HttpKlientRequest,
        responseType: KType,
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
     * Konfigurasjon av en [HttpKlient].
     * Alle felter minus clock har defaultverdier som gjør at klienten fungerer ut av boksen uten resilience, auth eller logging.
     * Per-request overstyringer via [RequestBuilder] tar alltid presedens over disse defaultene.
     */
    class HttpKlientConfig(
        /**
         * Klokken som brukes til all tidsmåling i klienten (retry-timing, metadata, auth-token-utløp).
         * Påkrevd; ingen default i produksjonskode (se AGENTS.md).
         */
        val clock: Clock,
    ) {
        /**
         * Connect-timeout for den underliggende `java.net.http.HttpClient`.
         * Brukes kun ved opprettelse av selve [java.net.http.HttpClient]-instansen.
         */
        var connectTimeout: Duration = 10.seconds

        /**
         * Default per-request timeout.
         * Kan overstyres per request via [RequestBuilder.timeout].
         */
        var defaultTimeout: Duration = 30.seconds

        /**
         * Redirect-policy for den underliggende `java.net.http.HttpClient`.
         * Default er [HttpClient.Redirect.NEVER] slik at konsumenten ser `3xx`-svar eksplisitt i stedet for at klienten følger dem stille.
         * Sett til [HttpClient.Redirect.NORMAL] for å følge redirects (utenom HTTPS→HTTP-nedgradering).
         */
        var followRedirects: HttpClient.Redirect = HttpClient.Redirect.NEVER

        /**
         * Default predikat for hvilke HTTP-statuser som regnes som suksess.
         * Kan overstyres per request via [RequestBuilder.successStatus].
         */
        var successStatus: (Int) -> Boolean = HttpStatusSuccess.is2xx

        /**
         * Default logging-config.
         * [HttpKlientLoggingConfig.Disabled] betyr ingen logging.
         * Kan overstyres per request via [RequestBuilder.logging] / [RequestBuilder.disableLogging].
         */
        var logging: HttpKlientLoggingConfig = HttpKlientLoggingConfig.Disabled

        /**
         * Default retry-config.
         * [RetryConfig.None] betyr ingen retries.
         * Kan overstyres per request via [RequestBuilder.retryConfig].
         */
        var defaultRetry: RetryConfig = RetryConfig.None

        /**
         * Default circuit breaker-config.
         * [CircuitBreakerConfig.None] betyr ingen circuit breaker.
         * Kan overstyres per request via [RequestBuilder.circuitBreakerConfig].
         * State er lokal til den enkelte [HttpKlient]-instansen.
         */
        var defaultCircuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig.None

        /**
         * Valgfri token-provider.
         * Når satt vil klienten kalle [AuthTokenProvider.hentToken] foran hver request og legge resultatet i `Authorization: Bearer <token>`-headeren (med mindre konsumenten allerede har satt `Authorization` eksplisitt, eller har satt en per-request token via [RequestBuilder.bearerToken]).
         * Hvis providern kaster, returneres [HttpKlientError.AuthError] og det HTTP-kallet gjøres aldri.
         *
         * Providern kalles med `skipCache = false` på det første forsøket.
         * Hvis serveren svarer med en status i [skipCacheRetryStatuses], gjør klienten _ett_ nytt forsøk der providern kalles med `skipCache = true`, slik at et eventuelt cachet, men avvist, token kan byttes ut med et ferskt.
         * Denne skip-cache-retryen skjer kun når providern faktisk styrer `Authorization`-headeren (dvs. verken per-request [RequestBuilder.bearerToken] eller en eksplisitt `Authorization`-header er satt).
         */
        var authTokenProvider: AuthTokenProvider? = null

        /**
         * HTTP-statuser som utløser skip-cache-retryen beskrevet på [authTokenProvider].
         *
         * Default er kun `401 Unauthorized` — den statusen betyr utvetydig at tokenet ble avvist av autentiseringen, og et ferskt token kan hjelpe.
         * `403 Forbidden` er bevisst _ikke_ med som default: for mange API-er er `403` et persistent tilgangsavslag (ABAC, manglende rolle på ressursen — ikke på tokenet), og en skip-cache-retry ville da doblet trafikken (ekstra Texas-kall forbi cachen + ekstra kall mot target) uten å hjelpe.
         * Konsumenter der `403` faktisk betyr «token-permissions har endret seg» kan opt-e inn ved å sette `skipCacheRetryStatuses = setOf(401, 403)`.
         * Tom mengde slår skip-cache-retryen helt av.
         */
        var skipCacheRetryStatuses: Set<Int> = setOf(401)
    }
}

suspend inline fun <reified Response : Any> HttpKlient.request(
    uri: URI,
    method: HttpMethod,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    val responseType = typeOf<Response>()
    return request(
        RequestBuilder(uri).apply { build.invoke(this) }.materialize(responseType, method),
        responseType,
    )
}

suspend inline fun <reified Response : Any> HttpKlient.get(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    val responseType = typeOf<Response>()
    return request(
        RequestBuilder(uri).apply { build.invoke(this) }.materialize(responseType, HttpMethod.GET),
        responseType,
    )
}

suspend inline fun <reified Response : Any> HttpKlient.post(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    val responseType = typeOf<Response>()
    return request(
        RequestBuilder(uri).apply { build.invoke(this) }.materialize(responseType, HttpMethod.POST),
        responseType,
    )
}

suspend inline fun <reified Response : Any> HttpKlient.put(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    val responseType = typeOf<Response>()
    return request(
        RequestBuilder(uri).apply { build.invoke(this) }.materialize(responseType, HttpMethod.PUT),
        responseType,
    )
}

suspend inline fun <reified Response : Any> HttpKlient.patch(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    val responseType = typeOf<Response>()
    return request(
        RequestBuilder(uri).apply { build.invoke(this) }.materialize(responseType, HttpMethod.PATCH),
        responseType,
    )
}

suspend inline fun <reified Response : Any> HttpKlient.delete(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    val responseType = typeOf<Response>()
    return request(
        RequestBuilder(uri).apply { build.invoke(this) }.materialize(responseType, HttpMethod.DELETE),
        responseType,
    )
}

suspend inline fun <reified Response : Any> HttpKlient.head(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    val responseType = typeOf<Response>()
    return request(
        RequestBuilder(uri).apply { build.invoke(this) }.materialize(responseType, HttpMethod.HEAD),
        responseType,
    )
}

suspend inline fun <reified Response : Any> HttpKlient.options(
    uri: URI,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> {
    val responseType = typeOf<Response>()
    return request(
        RequestBuilder(uri).apply { build.invoke(this) }.materialize(responseType, HttpMethod.OPTIONS),
        responseType,
    )
}

/**
 * Shorthand for det vanligste POST-tilfellet: serialiser [body]-DTO-en til JSON via `tiltakspenger-libs/json`.
 * Tilsvarer `post<R>(uri) { json(body); build() }`.
 *
 * For en allerede serialisert JSON-`String` finnes en egen [String]-overload som sender strengen verbatim (uten å serialisere den på nytt).
 * Hvis du trenger å sende rå tekst (ikke-JSON) eller en spesifikk `Content-Type`, bruk lambda-formen [post] med [RequestBuilder.body] / [RequestBuilder.header].
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
 * Shorthand for POST av en allerede serialisert JSON-`String`.
 * Strengen sendes verbatim som body (den serialiseres _ikke_ på nytt), og `Content-Type: application/json` settes hvis den mangler.
 * Denne overloaden finnes nettopp for å unngå at en JSON-streng dobbelt-serialiseres slik [post] med `body: Any` ville gjort.
 */
suspend inline fun <reified Response : Any> HttpKlient.post(
    uri: URI,
    body: String,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> = post(uri) {
    json(body)
    build.invoke(this)
}

/**
 * Shorthand for det vanligste PUT-tilfellet: serialiser [body]-DTO-en til JSON via `tiltakspenger-libs/json`.
 * Se [post] for [String]-overloaden og når lambda-formen skal foretrekkes.
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
 * Shorthand for PUT av en allerede serialisert JSON-`String`, sendt verbatim.
 * Se [post] med `body: String` for detaljer.
 */
suspend inline fun <reified Response : Any> HttpKlient.put(
    uri: URI,
    body: String,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> = put(uri) {
    json(body)
    build.invoke(this)
}

/**
 * Shorthand for det vanligste PATCH-tilfellet: serialiser [body]-DTO-en til JSON via `tiltakspenger-libs/json`.
 * Se [post] for [String]-overloaden og når lambda-formen skal foretrekkes.
 */
suspend inline fun <reified Response : Any> HttpKlient.patch(
    uri: URI,
    body: Any,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> = patch(uri) {
    json(body)
    build.invoke(this)
}

/**
 * Shorthand for PATCH av en allerede serialisert JSON-`String`, sendt verbatim.
 * Se [post] med `body: String` for detaljer.
 */
suspend inline fun <reified Response : Any> HttpKlient.patch(
    uri: URI,
    body: String,
    crossinline build: RequestBuilder.() -> Unit = {},
): Either<HttpKlientError, HttpKlientResponse<Response>> = patch(uri) {
    json(body)
    build.invoke(this)
}
