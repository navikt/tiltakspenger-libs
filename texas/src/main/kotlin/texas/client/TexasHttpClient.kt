package no.nav.tiltakspenger.libs.texas.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson3.jackson
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.log
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.kotlinModule
import java.time.Clock
import java.time.Duration

/**
 * [TexasClient] mot NAIS Texas-sidecaren (token exchange as a service).
 *
 * ## Statuser fra token-endepunktene
 * `/api/v1/token` ([getSystemToken]) og `/api/v1/token/exchange` ([exchangeToken]) svarer per Texas' OpenAPI-spec med `200 OK` (token), `400 Bad Request` eller `500 Internal Server Error` — se [OpenAPI-spec](https://doc.nais.io/auth/reference/#openapi-specification).
 * Texas' token-endepunkter returnerer altså _ikke_ `401`/`403`.
 * En `401 Unauthorized`/`403 Forbidden` kommer fra _target-API-et_ du kaller med det utstedte tokenet, ikke fra Texas selv.
 *
 * Denne klienten inspiserer eller transformerer ikke statuskoden fra Texas.
 * `HttpClient` er satt opp med `expectSuccess = false`, så Ktor kaster _ikke_ [io.ktor.client.plugins.ResponseException] for ikke-2xx-svar; klienten forsøker kun å deserialisere responsbodyen til [TexasTokenResponse], og en ikke-2xx-status vil derfor typisk boble opp som en deserialiseringsfeil som logges og kastes videre.
 *
 * ## Caching og `skip_cache` ([skipCache])
 * Texas cacher tokens: endepunktet returnerer alltid et cachet token hvis det finnes, og aldri et utløpt token.
 * `skipCache = true` setter `skip_cache` i requesten og tvinger Texas til å gå forbi cachen og hente et ferskt token fra identity provideren (f.eks. Entra ID).
 * Per NAIS-docs er dette kun nødvendig når target-API-et avviser tokenet, f.eks. fordi tilganger har endret seg siden tokenet ble utstedt — se [Consume internal API as an application](https://doc.nais.io/auth/entra-id/how-to/consume-m2m/#acquire-token).
 * Beslutningen om _når_ et avvist token skal trigge et nytt kall med `skipCache = true` (typisk ved `401` fra target-API-et) ligger hos kalleren, f.eks. `httpklient` sin `authTokenProvider` sammen med `skipCacheRetryStatuses`.
 */
class TexasHttpClient(
    private val introspectionUrl: String,
    private val tokenUrl: String,
    private val tokenExchangeUrl: String,
    private val timeoutSeconds: Long = 5L,
    private val httpClient: HttpClient = HttpClient(Apache5).config {
        install(ContentNegotiation) {
            jackson {
                addModule(kotlinModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = Duration.ofSeconds(timeoutSeconds).toMillis()
            requestTimeoutMillis = Duration.ofSeconds(timeoutSeconds).toMillis()
            socketTimeoutMillis = Duration.ofSeconds(timeoutSeconds).toMillis()
        }
        expectSuccess = false
    },
    private val clock: Clock,
) : TexasClient {
    override suspend fun introspectToken(
        token: String,
        identityProvider: IdentityProvider,
    ): TexasIntrospectionResponse {
        val texasIntrospectionRequest = TexasIntrospectionRequest(
            identityProvider = identityProvider.value,
            token = token,
        )
        try {
            val response =
                httpClient.post(introspectionUrl) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(texasIntrospectionRequest)
                }
            return response.body<TexasIntrospectionResponse>()
        } catch (e: Exception) {
            if (e is ResponseException) {
                log.error { "Kall for autentisering mot Texas feilet, responskode ${e.response.status}" }
            }
            log.error { "Kall for autentisering mot Texas feilet, se sikker logg for detaljer" }
            Sikkerlogg.error(e) { "Kall for autentisering mot Texas feilet, melding: ${e.message}" }
            throw e
        }
    }

    override suspend fun getSystemToken(
        audienceTarget: String,
        identityProvider: IdentityProvider,
        rewriteAudienceTarget: Boolean,
        skipCache: Boolean,
    ): AccessToken {
        val texasTokenRequest = TexasTokenRequest(
            identityProvider = identityProvider.value,
            target = if (rewriteAudienceTarget) {
                val target = audienceTarget.replace(':', '.')
                "api://$target/.default"
            } else {
                audienceTarget
            },
            skipCache = skipCache,
        )
        try {
            val response =
                httpClient.post(tokenUrl) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(texasTokenRequest)
                }
            val texasTokenResponse = response.body<TexasTokenResponse>()
            return texasTokenResponse.toAccessToken(clock = clock)
        } catch (e: Exception) {
            if (e is ResponseException) {
                log.error { "Kall for å hente token mot Texas feilet, responskode ${e.response.status}" }
            }
            log.error { "Kall å hente token mot Texas feilet, se sikker logg for detaljer" }
            Sikkerlogg.error(e) { "Kall å hente token mot Texas feilet, melding: ${e.message}" }
            throw e
        }
    }

    override suspend fun exchangeToken(
        userToken: String,
        audienceTarget: String,
        identityProvider: IdentityProvider,
        skipCache: Boolean,
    ): AccessToken {
        val texasExchangeTokenRequest = TexasExchangeTokenRequest(
            identityProvider = identityProvider.value,
            target = audienceTarget,
            userToken = userToken,
            skipCache = skipCache,
        )
        try {
            val response =
                httpClient.post(tokenExchangeUrl) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(texasExchangeTokenRequest)
                }
            val texasTokenResponse = response.body<TexasTokenResponse>()
            return texasTokenResponse.toAccessToken(clock = clock)
        } catch (e: Exception) {
            if (e is ResponseException) {
                log.error { "Kall for å veksle token mot Texas feilet, responskode ${e.response.status}" }
            }
            log.error { "Kall å veksle token mot Texas feilet, se sikker logg for detaljer" }
            Sikkerlogg.error(e) { "Kall å veksle token mot Texas feilet, melding: ${e.message}" }
            throw e
        }
    }
}
