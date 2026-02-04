package no.nav.tiltakspenger.libs.texas.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
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
import java.time.Duration

class TexasHttpClient(
    private val introspectionUrl: String,
    private val tokenUrl: String,
    private val tokenExchangeUrl: String,
    private val timeoutSeconds: Long = 5L,
    private val httpClient: HttpClient = HttpClient(Apache).config {
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
    ): AccessToken {
        val texasTokenRequest = TexasTokenRequest(
            identityProvider = identityProvider.value,
            target = if (rewriteAudienceTarget) {
                val target = audienceTarget.replace(':', '.')
                "api://$target/.default"
            } else {
                audienceTarget
            },
        )
        try {
            val response =
                httpClient.post(tokenUrl) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(texasTokenRequest)
                }
            val texasTokenResponse = response.body<TexasTokenResponse>()
            return texasTokenResponse.toAccessToken()
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
    ): AccessToken {
        val texasExchangeTokenRequest = TexasExchangeTokenRequest(
            identityProvider = identityProvider.value,
            target = audienceTarget,
            userToken = userToken,
        )
        try {
            val response =
                httpClient.post(tokenExchangeUrl) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(texasExchangeTokenRequest)
                }
            val texasTokenResponse = response.body<TexasTokenResponse>()
            return texasTokenResponse.toAccessToken()
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
