package no.nav.tiltakspenger.libs.texas.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.auth.test.core.JwtGenerator
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import org.junit.jupiter.api.Test
import java.io.IOException

internal class TexasHttpClientTest {
    private val clock = fixedClock
    private val jwtGenerator = JwtGenerator(clock = clock)
    private val introspectionUrl = "http://texas.local/introspect"
    private val tokenUrl = "http://texas.local/token"
    private val tokenExchangeUrl = "http://texas.local/tokenexchange"

    private fun texasClient(transport: FakeHttpTransport): TexasHttpClient = TexasHttpClient(
        introspectionUrl = introspectionUrl,
        tokenUrl = tokenUrl,
        tokenExchangeUrl = tokenExchangeUrl,
        clock = clock,
        transport = transport,
    )

    @Test
    fun `ekstern bruker, gyldig token - riktige claims`() = runTest {
        val fnr = FnrGenerator().generer().verdi
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
        val transport = FakeHttpTransport().apply { leggIKøJson(json = response) }
        val jwt = jwtGenerator.createJwtForUser(
            fnr = fnr,
            issuer = "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
            subject = "test-subject",
            azpName = "dev-gcp:tpts:tiltakspenger-meldekort",
            azp = "744e4092-4215-4e02-87df-a61aaf1b95b5",
            audience = "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
            acr = "idporten-loa-high",
        )

        val introspectionResponse = texasClient(transport).introspectToken(jwt, IdentityProvider.TOKENX)

        introspectionResponse.active shouldBe true
        introspectionResponse.error shouldBe null
        introspectionResponse.groups shouldBe null
        introspectionResponse.roles shouldBe null
        introspectionResponse.other["pid"] shouldBe fnr
        introspectionResponse.other["acr"] shouldBe "idporten-loa-high"
        introspectionResponse.other["azp_name"] shouldBe "dev-gcp:tpts:tiltakspenger-meldekort"
        introspectionResponse.other["azp"] shouldBe "744e4092-4215-4e02-87df-a61aaf1b95b5"

        val mottatt = transport.mottatteKall.single()
        mottatt.uri.toString() shouldBe introspectionUrl
        mottatt.request.headers().firstValue("Content-Type").get() shouldBe "application/json"
        val requestBody = deserialize<Map<String, Any?>>(mottatt.bodyTekst)
        requestBody["identity_provider"] shouldBe "tokenx"
        requestBody["token"] shouldBe jwt
    }

    @Test
    fun `saksbehandler, gyldig token - riktig respons`() = runTest {
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
        val transport = FakeHttpTransport().apply { leggIKøJson(json = response) }
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

        val introspectionResponse = texasClient(transport).introspectToken(jwt, IdentityProvider.AZUREAD)

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
    fun `systembruker, gyldig token - riktig respons`() = runTest {
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
        val transport = FakeHttpTransport().apply { leggIKøJson(json = response) }
        val jwt = jwtGenerator.createJwtForSystembruker(
            issuer = "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
            subject = "test-subject",
            azpName = "dev-fss:tpts:tiltakspenger-saksbehandling-api",
            azp = "744e4092-4215-4e02-87df-a61aaf1b95b5",
            audience = "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
            roles = listOf("TEST_ROLLE_1", "TEST_ROLLE_2"),
        )

        val introspectionResponse = texasClient(transport).introspectToken(jwt, IdentityProvider.AZUREAD)

        introspectionResponse.active shouldBe true
        introspectionResponse.error shouldBe null
        introspectionResponse.groups shouldBe null
        introspectionResponse.roles shouldBe listOf("TEST_ROLLE_1", "TEST_ROLLE_2")
        introspectionResponse.other["idtyp"] shouldBe "app"
        introspectionResponse.other["azp_name"] shouldBe "dev-fss:tpts:tiltakspenger-saksbehandling-api"
        introspectionResponse.other["azp"] shouldBe "744e4092-4215-4e02-87df-a61aaf1b95b5"
    }

    @Test
    fun `saksbehandler, utløpt token - riktig respons`() = runTest {
        val response = """
            {
                "active": false,
                "error": "token is expired"
            }
        """.trimIndent()
        val transport = FakeHttpTransport().apply { leggIKøJson(json = response) }
        val jwt = jwtGenerator.createJwtForSaksbehandler()

        val introspectionResponse = texasClient(transport).introspectToken(jwt, IdentityProvider.AZUREAD)

        introspectionResponse.active shouldBe false
        introspectionResponse.error shouldNotBe null
        introspectionResponse.groups shouldBe null
        introspectionResponse.roles shouldBe null
        introspectionResponse.other shouldBe emptyMap()
    }

    @Test
    fun `getSystemToken skriver om audience target og videreformidler skip_cache`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "abc123", "expires_in": 3600, "token_type": "Bearer"}""")
        }

        val accessToken = texasClient(transport).getSystemToken(
            audienceTarget = "dev-gcp:tpts:tiltakspenger-datadeling",
            identityProvider = IdentityProvider.AZUREAD,
            skipCache = true,
        )

        accessToken.token shouldBe "abc123"
        accessToken.expiresAt shouldBe clock.instant().plusSeconds(3600)
        val mottatt = transport.mottatteKall.single()
        mottatt.uri.toString() shouldBe tokenUrl
        val requestBody = deserialize<Map<String, Any?>>(mottatt.bodyTekst)
        requestBody["identity_provider"] shouldBe "azuread"
        requestBody["target"] shouldBe "api://dev-gcp.tpts.tiltakspenger-datadeling/.default"
        requestBody["skip_cache"] shouldBe true
    }

    @Test
    fun `getSystemToken lar ferdig utskrevet audience target stå`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "abc123", "expires_in": 3600}""")
        }

        texasClient(transport).getSystemToken(
            audienceTarget = "api://dev-fss.teamdokumenthandtering.saf/.default",
            identityProvider = IdentityProvider.AZUREAD,
        )

        val requestBody = deserialize<Map<String, Any?>>(transport.mottatteKall.single().bodyTekst)
        requestBody["target"] shouldBe "api://dev-fss.teamdokumenthandtering.saf/.default"
        requestBody["skip_cache"] shouldBe false
    }

    /**
     * Flagget er utgått og skal ikke lenger kunne velte et kall.
     * Dette er nøyaktig kombinasjonen som tok ned `tiltakspenger-soknad-api` i produksjon: kortform fra nais-manifestet, med omskrivingen slått av.
     */
    @Test
    fun `getSystemToken normaliserer også når det utgåtte rewrite-flagget er av`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "abc123", "expires_in": 3600}""")
        }

        texasClient(transport).getSystemToken(
            audienceTarget = "prod-fss:pdl:pdl-api",
            identityProvider = IdentityProvider.AZUREAD,
            rewriteAudienceTarget = false,
        )

        val requestBody = deserialize<Map<String, Any?>>(transport.mottatteKall.single().bodyTekst)
        requestBody["target"] shouldBe "api://prod-fss.pdl.pdl-api/.default"
    }

    @Test
    fun `getSystemToken lar eksterne API-er utenfor nais stå urørt`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "graph-token", "expires_in": 3600}""")
        }

        texasClient(transport).getSystemToken(
            audienceTarget = "https://graph.microsoft.com/.default",
            identityProvider = IdentityProvider.AZUREAD,
        )

        val requestBody = deserialize<Map<String, Any?>>(transport.mottatteKall.single().bodyTekst)
        requestBody["target"] shouldBe "https://graph.microsoft.com/.default"
    }

    @Test
    fun `getSystemToken lar target for andre identity providere enn Azure AD stå urørt`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "mp-token", "expires_in": 3600}""")
        }

        texasClient(transport).getSystemToken(
            audienceTarget = "nav:tiltakspenger/vedtak",
            identityProvider = IdentityProvider.MASKINPORTEN,
        )

        val requestBody = deserialize<Map<String, Any?>>(transport.mottatteKall.single().bodyTekst)
        requestBody["target"] shouldBe "nav:tiltakspenger/vedtak"
    }

    @Test
    fun `exchangeToken sender user_token og target uendret`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "obo-token", "expires_in": 60}""")
        }

        val accessToken = texasClient(transport).exchangeToken(
            userToken = "bruker-jwt",
            audienceTarget = "api://dev-gcp.tpts.tilgangsmaskin/.default",
            identityProvider = IdentityProvider.AZUREAD,
        )

        accessToken.token shouldBe "obo-token"
        val mottatt = transport.mottatteKall.single()
        mottatt.uri.toString() shouldBe tokenExchangeUrl
        val requestBody = deserialize<Map<String, Any?>>(mottatt.bodyTekst)
        requestBody["user_token"] shouldBe "bruker-jwt"
        requestBody["target"] shouldBe "api://dev-gcp.tpts.tilgangsmaskin/.default"
        requestBody["skip_cache"] shouldBe false
    }

    /**
     * TokenX bruker `cluster:namespace:app` som target.
     * Normaliseringen for Azure AD må ikke røre den formen — gjør den det, slutter alle innloggede brukerkall å virke.
     */
    @Test
    fun `exchangeToken lar TokenX-target stå på kortformen`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "tokenx-token", "expires_in": 60}""")
        }

        texasClient(transport).exchangeToken(
            userToken = "bruker-jwt",
            audienceTarget = "prod-fss:pdl:pdl-api",
            identityProvider = IdentityProvider.TOKENX,
        )

        val requestBody = deserialize<Map<String, Any?>>(transport.mottatteKall.single().bodyTekst)
        requestBody["target"] shouldBe "prod-fss:pdl:pdl-api"
    }

    @Test
    fun `exchangeToken normaliserer on-behalf-of-target for Azure AD`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "obo-token", "expires_in": 60}""")
        }

        texasClient(transport).exchangeToken(
            userToken = "bruker-jwt",
            audienceTarget = "prod-fss:teamdokumenthandtering:saf",
            identityProvider = IdentityProvider.AZUREAD,
        )

        val requestBody = deserialize<Map<String, Any?>>(transport.mottatteKall.single().bodyTekst)
        requestBody["target"] shouldBe "api://prod-fss.teamdokumenthandtering.saf/.default"
    }

    @Test
    fun `ikke-2xx ved exchangeToken kaster RuntimeException med responskoden`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøStatus(statusCode = 400, body = """{"error": "invalid request"}""")
        }

        val exception = shouldThrow<RuntimeException> {
            texasClient(transport).exchangeToken(
                userToken = "bruker-jwt",
                audienceTarget = "api://dev-gcp.tpts.tilgangsmaskin/.default",
                identityProvider = IdentityProvider.AZUREAD,
            )
        }

        exception.message shouldContain "responskode 400"
    }

    @Test
    fun `klienten kan konstrueres uten transport - produksjonstransporten er default`() {
        // Dekker default-uttrykket for transport; ingen nettverkskall gjøres ved konstruksjon.
        TexasHttpClient(
            introspectionUrl = introspectionUrl,
            tokenUrl = tokenUrl,
            tokenExchangeUrl = tokenExchangeUrl,
            clock = clock,
        )
    }

    @Test
    fun `ikke-2xx fra Texas kaster RuntimeException med responskoden`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøStatus(statusCode = 400, body = """{"error": "invalid request"}""")
        }

        val exception = shouldThrow<RuntimeException> {
            texasClient(transport).getSystemToken(
                audienceTarget = "dev-gcp:tpts:tiltakspenger-datadeling",
                identityProvider = IdentityProvider.AZUREAD,
            )
        }

        exception.message shouldContain "responskode 400"
    }

    @Test
    fun `nettverksfeil mot Texas kaster den underliggende exceptionen, som gammel klient`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøKast(IOException("connection refused"))
        }

        val exception = shouldThrow<IOException> {
            texasClient(transport).introspectToken("token", IdentityProvider.AZUREAD)
        }

        exception.message shouldBe "connection refused"
    }

    @Test
    fun `udeserialiserbar 200-respons kaster den underliggende parse-feilen`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = "dette er ikke json")
        }

        shouldThrow<Exception> {
            texasClient(transport).getSystemToken(
                audienceTarget = "dev-gcp:tpts:tiltakspenger-datadeling",
                identityProvider = IdentityProvider.AZUREAD,
            )
        }
    }

    @Test
    fun `TexasSystemTokenProvider videreformidler skipCache til Texas`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "system-token", "expires_in": 120}""")
        }
        val provider = TexasSystemTokenProvider(
            texasClient = texasClient(transport),
            audienceTarget = "dev-gcp:tpts:tiltakspenger-datadeling",
        )

        val accessToken = provider.hentToken(skipCache = true)

        accessToken.token shouldBe "system-token"
        val requestBody = deserialize<Map<String, Any?>>(transport.mottatteKall.single().bodyTekst)
        requestBody["target"] shouldBe "api://dev-gcp.tpts.tiltakspenger-datadeling/.default"
        requestBody["skip_cache"] shouldBe true
    }
}
