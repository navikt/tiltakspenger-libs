package no.nav.tiltakspenger.libs.texas.client

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson3.jackson
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.auth.test.core.JwtGenerator
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.kotlinModule
import java.time.Duration

class TexasHttpClientTest {
    val jwtGenerator = JwtGenerator()
    val introspectionUrl = "/introspect"
    val tokenUrl = "/token"
    val tokenExchangeUrl = "/tokenexchange"

    @Test
    fun `ekstern bruker, gyldig token - riktige claims`(): Unit = runBlocking {
        val fnr = "12345678910"
        val response = """
            {
                "active": true,
                "iss": "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
                "sub": "test-subject",
                "aud": "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
                "exp": 1755585152,
                "iat": 1755583347,
                "nbf": 1755583347,
                "pid": "$fnr",
                "azp_name": "dev-gcp:tpts:tiltakspenger-meldekort",
                "azp": "744e4092-4215-4e02-87df-a61aaf1b95b5",
                "ver": "2.0",
                "acr": "idporten-loa-high"
            }
        """.trimIndent()
        val httpClient = getMockHttpClient(response)
        val texasClient = TexasHttpClient(introspectionUrl, tokenUrl, tokenExchangeUrl, httpClient = httpClient)
        val jwt = jwtGenerator.createJwtForUser(
            fnr = fnr,
            issuer = "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
            subject = "test-subject",
            azpName = "dev-gcp:tpts:tiltakspenger-meldekort",
            azp = "744e4092-4215-4e02-87df-a61aaf1b95b5",
            audience = "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
            acr = "idporten-loa-high",
        )

        val introspectionResponse = texasClient.introspectToken(jwt, IdentityProvider.TOKENX)

        introspectionResponse.active shouldBe true
        introspectionResponse.error shouldBe null
        introspectionResponse.groups shouldBe null
        introspectionResponse.roles shouldBe null
        introspectionResponse.other["pid"] shouldBe fnr
        introspectionResponse.other["acr"] shouldBe "idporten-loa-high"
        introspectionResponse.other["azp_name"] shouldBe "dev-gcp:tpts:tiltakspenger-meldekort"
        introspectionResponse.other["azp"] shouldBe "744e4092-4215-4e02-87df-a61aaf1b95b5"
    }

    @Test
    fun `saksbehandler, gyldig token - riktig respons`(): Unit = runBlocking {
        val epost = "Sak.Behandler@nav.no"
        val navIdent = "Z12345"
        val response = """
            {
                "active": true,
                "iss": "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
                "sub": "test-subject",
                "aud": "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
                "exp": 1755585152,
                "iat": 1755583347,
                "nbf": 1755583347,
                "preferred_username": "$epost",
                "azp_name": "dev-fss:tpts:tiltakspenger-saksbehandling-api",
                "azp": "744e4092-4215-4e02-87df-a61aaf1b95b5",
                "NAVident": "$navIdent",
                "groups": [
                    "1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"
                ],
                "ver": "2.0"
            }
        """.trimIndent()
        val httpClient = getMockHttpClient(response)
        val texasClient = TexasHttpClient(introspectionUrl, tokenUrl, tokenExchangeUrl, httpClient = httpClient)
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
            subject = "test-subject",
            preferredUsername = epost,
            azpName = "dev-fss:tpts:tiltakspenger-saksbehandling-api",
            azp = "744e4092-4215-4e02-87df-a61aaf1b95b5",
            navIdent = "Z12345",
            name = "Sak Behandler",
            audience = "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
            groups = listOf("ROLE_SAKSBEHANDLER"),
        )

        val introspectionResponse = texasClient.introspectToken(jwt, IdentityProvider.AZUREAD)

        introspectionResponse.active shouldBe true
        introspectionResponse.error shouldBe null
        introspectionResponse.groups shouldBe listOf("1b3a2c4d-d620-4fcf-a29b-a6cdadf29680")
        introspectionResponse.roles shouldBe null
        introspectionResponse.other["NAVident"] shouldBe navIdent
        introspectionResponse.other["preferred_username"] shouldBe epost
        introspectionResponse.other["azp_name"] shouldBe "dev-fss:tpts:tiltakspenger-saksbehandling-api"
        introspectionResponse.other["azp"] shouldBe "744e4092-4215-4e02-87df-a61aaf1b95b5"
    }

    @Test
    fun `systembruker, gyldig token - riktig respons`(): Unit = runBlocking {
        val response = """
            {
                "active": true,
                "iss": "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
                "sub": "test-subject",
                "aud": "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
                "exp": 1755585152,
                "iat": 1755583347,
                "nbf": 1755583347,
                "idtyp": "app",
                "azp_name": "dev-fss:tpts:tiltakspenger-saksbehandling-api",
                "azp": "744e4092-4215-4e02-87df-a61aaf1b95b5",
                "roles": [
                    "TEST_ROLLE_1",
                    "TEST_ROLLE_2"
                ],
                "ver": "2.0"
            }
        """.trimIndent()
        val httpClient = getMockHttpClient(response)
        val texasClient = TexasHttpClient(introspectionUrl, tokenUrl, tokenExchangeUrl, httpClient = httpClient)
        val jwt = jwtGenerator.createJwtForSystembruker(
            issuer = "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
            subject = "test-subject",
            azpName = "dev-fss:tpts:tiltakspenger-saksbehandling-api",
            azp = "744e4092-4215-4e02-87df-a61aaf1b95b5",
            audience = "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
            roles = listOf("TEST_ROLLE_1", "TEST_ROLLE_2"),
        )

        val introspectionResponse = texasClient.introspectToken(jwt, IdentityProvider.AZUREAD)

        introspectionResponse.active shouldBe true
        introspectionResponse.error shouldBe null
        introspectionResponse.groups shouldBe null
        introspectionResponse.roles shouldBe listOf("TEST_ROLLE_1", "TEST_ROLLE_2")
        introspectionResponse.other["idtyp"] shouldBe "app"
        introspectionResponse.other["azp_name"] shouldBe "dev-fss:tpts:tiltakspenger-saksbehandling-api"
        introspectionResponse.other["azp"] shouldBe "744e4092-4215-4e02-87df-a61aaf1b95b5"
    }

    @Test
    fun `saksbehandler, utløpt token - riktig respons`(): Unit = runBlocking {
        val response = """
            {
                "active": false,
                "error": "token is expired"
            }
        """.trimIndent()
        val httpClient = getMockHttpClient(response)
        val texasClient = TexasHttpClient(introspectionUrl, tokenUrl, tokenExchangeUrl, httpClient = httpClient)
        val jwt = jwtGenerator.createJwtForSaksbehandler()

        val introspectionResponse = texasClient.introspectToken(jwt, IdentityProvider.AZUREAD)

        introspectionResponse.active shouldBe false
        introspectionResponse.error shouldNotBe null
        introspectionResponse.groups shouldBe null
        introspectionResponse.roles shouldBe null
        introspectionResponse.other shouldBe emptyMap()
    }

    private fun getMockHttpClient(responseJson: String): HttpClient {
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                introspectionUrl -> respond(
                    content = responseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                else -> error("Unhandled request ${request.url}")
            }
        }

        return HttpClient(mockEngine).config {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                jackson {
                    addModule(kotlinModule())
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = Duration.ofSeconds(5L).toMillis()
                requestTimeoutMillis = Duration.ofSeconds(5L).toMillis()
                socketTimeoutMillis = Duration.ofSeconds(5L).toMillis()
            }
            expectSuccess = false
        }
    }
}
