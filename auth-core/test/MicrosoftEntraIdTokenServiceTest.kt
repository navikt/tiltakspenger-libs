package no.nav.tiltakspenger.libs.auth.core

import arrow.core.left
import com.github.tomakehurst.wiremock.WireMockServer
import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.get
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.GenerellSystembruker
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerrolle
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerroller
import no.nav.tiltakspenger.libs.common.Saksbehandler
import no.nav.tiltakspenger.libs.common.Saksbehandlerrolle
import no.nav.tiltakspenger.libs.common.Saksbehandlerroller
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Suppress("UNCHECKED_CAST")
internal class MicrosoftEntraIdTokenServiceTest {

    @Test
    fun `mapper autoriserte roller riktig`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val saksbehandlerUUID = UUID.randomUUID()
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            groups = listOf(saksbehandlerUUID.toString()),
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = listOf(
                    AdRolle(
                        Saksbehandlerrolle.SAKSBEHANDLER,
                        saksbehandlerUUID.toString(),
                    ),
                ),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt).getOrNull()!! shouldBe Saksbehandler(
                    navIdent = "Z12345",
                    brukernavn = "Sak Behandler",
                    epost = "Sak.Behandler@nav.no",
                    roller = Saksbehandlerroller(listOf(Saksbehandlerrolle.SAKSBEHANDLER)),
                    scopes = TestSystembrukerroller(emptySet()) as GenerellSystembrukerroller<GenerellSystembrukerrolle>,
                    klientId = "744e4092-4215-4e02-87df-a61aaf1b95b5",
                    klientnavn = "dev-fss:tpts:tiltakspenger-saksbehandling-api",
                )
            }
        }
    }

    @Test
    fun `ukjent rolle skal filtreres bort`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val ukjentRolle = UUID.randomUUID()
        val jwtGenerator = JwtGenerator()
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            groups = listOf(ukjentRolle.toString()),
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = listOf(
                    AdRolle(
                        Saksbehandlerrolle.SAKSBEHANDLER,
                        UUID.randomUUID().toString(),
                    ),
                ),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwtGenerator.jwkAsString)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt).getOrNull()!! shouldBe Saksbehandler(
                    navIdent = "Z12345",
                    brukernavn = "Sak Behandler",
                    epost = "Sak.Behandler@nav.no",
                    roller = Saksbehandlerroller(emptyList()),
                    scopes = TestSystembrukerroller(emptySet()) as GenerellSystembrukerroller<GenerellSystembrukerrolle>,
                    klientId = "744e4092-4215-4e02-87df-a61aaf1b95b5",
                    klientnavn = "dev-fss:tpts:tiltakspenger-saksbehandling-api",
                )
            }
        }
    }

    @Test
    fun `andre kall cacher`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt).getOrNull()!!
                wiremock.verify(
                    1,
                    com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/"),
                    ),
                )
                tokenService.validerOgHentBruker(jwt).getOrNull()!!
                wiremock.verify(
                    1,
                    com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/"),
                    ),
                )
            }
        }
    }

    @Test
    fun `andre kall cacher ikke ved ukjent kid`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator1 = JwtGenerator()
        val jwk1 = jwtGenerator1.jwkAsString
        val jwt1 = jwtGenerator1.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
        )
        val jwtGenerator2 = JwtGenerator()
        val jwt2 = jwtGenerator2.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk1)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt1).getOrNull()!!
                wiremock.verify(
                    1,
                    com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/"),
                    ),
                )
                tokenService.validerOgHentBruker(jwt2) shouldBe Valideringsfeil.UgyldigToken.UlikKid.left()
                wiremock.verify(
                    2,
                    com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/"),
                    ),
                )
            }
        }
    }

    @Test
    fun `krever at jwt er signert av riktig jwk`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val keyId = UUID.randomUUID().toString()
        val jwtGenerator1 = JwtGenerator(jwkKeyId = keyId)
        val jwk1 = jwtGenerator1.jwkAsString
        val jwtGenerator2 = JwtGenerator()
        val jwt2 = jwtGenerator2.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            jwtKeyId = keyId,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk1)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt2) shouldBe Valideringsfeil.UgyldigToken.KunneIkkeVerifisereToken.left()
            }
        }
    }

    @Test
    fun `ugyldig JWT`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val invalidJwt = "invalid.jwt.token"

        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(invalidJwt) shouldBe Valideringsfeil.UgyldigToken.KunneIkkeDekodeToken.left()
            }
        }
    }

    @Test
    fun `token exp margin på 1 sekund feiler`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            expiresAt = Instant.now().plusMillis(1000),
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.KunneIkkeVerifisereToken.left()
            }
        }
    }

    @Test
    fun `token exp margin på 5 sekunder er OK`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            expiresAt = Instant.now().plusSeconds(5),
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt).shouldBeRight()
            }
        }
    }

    @Test
    fun `issued at tåler 5 sekunder leeway`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            issuedAt = Instant.now().plusSeconds(5),
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt).shouldBeRight()
            }
        }
    }

    @Test
    fun `issued at er for langt fram i tid`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            issuedAt = Instant.now().plusSeconds(10),
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.KunneIkkeVerifisereToken.left()
            }
        }
    }

    @Test
    fun `not before tåler 5 sekunder leeway`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            notBefore = Instant.now().plusSeconds(5),
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt).shouldBeRight()
            }
        }
    }

    @Test
    fun `not before er for langt fram i tid`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            notBefore = Instant.now().plusSeconds(10),
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.KunneIkkeVerifisereToken.left()
            }
        }
    }

    @Test
    fun `uforventet issuer`() {
        val issuer = "test-issuer"
        val wrongIssuer = "wrong-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
        )

        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = wrongIssuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.KunneIkkeVerifisereToken.left()
            }
        }
    }

    @Test
    fun `uforventet audience`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val ukjentAudience = "ukjent-audience"
        val jwtGenerator = JwtGenerator()
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = ukjentAudience,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.KunneIkkeVerifisereToken.left()
            }
        }
    }

    @Test
    fun `mangler jwk`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator()
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse("""{"keys":[]}""")

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.KunneIkkeHenteJwk.left()
            }
        }
    }

    @Test
    fun `jwk service unavailable`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator(jwkKeyId = "jwk-key-id")
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            jwtKeyId = "jwt-key-id",
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.get {
                url equalTo "/"
            } returns {
                statusCode = 503
                body = "Service Unavailable"
            }

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.KunneIkkeHenteJwk.left()
            }
        }
    }

    @Test
    fun `ulik kid`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator(jwkKeyId = "jwk-key-id")
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            jwtKeyId = "jwt-key-id",
        )

        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.UlikKid.left()
            }
        }
    }

    @Test
    fun `mangler NAVident claim`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator(jwkKeyId = "jwk-key-id")
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            navIdent = null,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.ManglerClaim(
                    "NAVident",
                )
                    .left()
            }
        }
    }

    @Test
    fun `mangler preferred_username claim`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator(jwkKeyId = "jwk-key-id")
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            preferredUsername = null,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.ManglerClaim(
                    "preferred_username",
                )
                    .left()
            }
        }
    }

    @Test
    fun `mangler groups claim`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator(jwkKeyId = "jwk-key-id")
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSaksbehandler(
            issuer = issuer,
            audience = clientId,
            groups = null,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.ManglerClaim(
                    "groups",
                )
                    .left()
            }
        }
    }

    @Test
    fun `systembruker mangler roles claim`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator(jwkKeyId = "jwk-key-id")
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSystembruker(
            issuer = issuer,
            audience = clientId,
            roles = null,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.ManglerClaim(
                    "roles",
                ).left()
            }
        }
    }

    @Test
    fun `systembruker mangler azp_name claim`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator(jwkKeyId = "jwk-key-id")
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSystembruker(
            issuer = issuer,
            audience = clientId,
            azpName = null,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.ManglerClaim(
                    "azp_name",
                )
                    .left()
            }
        }
    }

    @Test
    fun `systembruker mangler oid claim`() {
        val issuer = "test-issuer"
        val clientId = "test-client-id"
        val jwtGenerator = JwtGenerator(jwkKeyId = "jwk-key-id")
        val jwk = jwtGenerator.jwkAsString
        val jwt = jwtGenerator.createJwtForSystembruker(
            issuer = issuer,
            audience = clientId,
            oid = null,
        )
        withWireMockServer { wiremock ->
            val tokenService = MicrosoftEntraIdTokenService(
                url = wiremock.baseUrl(),
                issuer = issuer,
                clientId = clientId,
                autoriserteBrukerroller = emptyList(),
                systembrukerMapper = ::mapper as (String, String, Set<String>) -> GenerellSystembruker<GenerellSystembrukerrolle, GenerellSystembrukerroller<GenerellSystembrukerrolle>>,
            )
            wiremock.prepareJwkResponse(jwk)

            kotlinx.coroutines.test.runTest {
                tokenService.validerOgHentBruker(jwt) shouldBe Valideringsfeil.UgyldigToken.ManglerClaim(
                    "oid",
                ).left()
            }
        }
    }
}

private fun WireMockServer.prepareJwkResponse(jwk: String) {
    this.get {
        url equalTo "/"
    } returns {
        statusCode = 200
        header = "Content-Type" to "application/json"
        body = jwk
    }
}

private data class TestSystembruker(
    override val roller: TestSystembrukerroller,
    override val klientId: String,
    override val klientnavn: String,
) : GenerellSystembruker<TestSystembrukerrolle, TestSystembrukerroller>

private enum class TestSystembrukerrolle : GenerellSystembrukerrolle {
    LAGE_HENDELSER,
    HENTE_DATA,
}

private data class TestSystembrukerroller(
    override val value: Set<TestSystembrukerrolle>,
) : GenerellSystembrukerroller<TestSystembrukerrolle>,
    Set<TestSystembrukerrolle> by value {

    constructor(vararg roller: TestSystembrukerrolle) : this(roller.toSet())
    constructor(roller: Collection<TestSystembrukerrolle>) : this(roller.toSet())

    fun harLageHendelser(): Boolean = value.contains(TestSystembrukerrolle.LAGE_HENDELSER)
    fun harHenteData(): Boolean = value.contains(TestSystembrukerrolle.HENTE_DATA)
}

private fun mapper(
    klientId: String = "klientId",
    klientnavn: String = "klientnavn",
    roller: Set<String>,
): TestSystembruker {
    return TestSystembruker(
        roller = TestSystembrukerroller(
            roller.map {
                TestSystembrukerrolle.valueOf(it)
            }.toSet(),
        ),
        klientId = klientId,
        klientnavn = klientnavn,
    )
}
