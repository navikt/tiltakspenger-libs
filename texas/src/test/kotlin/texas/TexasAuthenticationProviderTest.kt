package no.nav.tiltakspenger.libs.texas

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.jackson3.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.util.url
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.auth.test.core.JwtGenerator
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.Saksbehandlerrolle
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.ktor.test.common.defaultRequest
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import org.junit.jupiter.api.Test

class TexasAuthenticationProviderTest {
    val clock = fixedClock
    val jwtGenerator = JwtGenerator(clock = clock)
    val texasClient = mockk<TexasClient>()

    @Test
    fun `ekstern bruker, gyldig token - autentiseres og riktig principal`() {
        val fnr = "12345678910"
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns TexasIntrospectionResponse(
            active = true,
            error = null,
            groups = null,
            roles = null,
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "acr" to "idporten-loa-high",
                "pid" to fnr,
            ),
        )
        runTest {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        register(ContentType.Application.Json, JacksonConverter(objectMapper))
                    }
                    authentication {
                        register(
                            TexasAuthenticationProvider(
                                TexasAuthenticationProvider.Config(
                                    name = IdentityProvider.TOKENX.value,
                                    texasClient = texasClient,
                                    identityProvider = IdentityProvider.TOKENX,
                                ),
                            ),
                        )
                    }
                    routing {
                        authenticate(IdentityProvider.TOKENX.value) {
                            get("/some-path") {
                                val fnr = call.fnr()
                                call.respond(message = fnr, status = HttpStatusCode.OK)
                            }
                        }
                    }
                }
                defaultRequest(
                    HttpMethod.Get,
                    url {
                        protocol = URLProtocol.HTTPS
                        path("/some-path")
                    },
                    jwt = jwtGenerator.createJwtForUser(fnr = fnr),
                ).apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe objectMapper.writeValueAsString(Fnr.fromString(fnr))
                }
            }
        }
    }

    @Test
    fun `saksbehandler, gyldig token - autentiseres og riktig principal`() {
        val alleAdRoller = listOf(
            AdRolle(Saksbehandlerrolle.SAKSBEHANDLER, "1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"),
            AdRolle(Saksbehandlerrolle.BESLUTTER, "79985315-b2de-40b8-a740-9510796993c6"),
            AdRolle(Saksbehandlerrolle.FORTROLIG_ADRESSE, "ea930b6b-9397-44d9-b9e6-f4cf527a632a"),
        )
        val epost = "Sak.Behandler@nav.no"
        val navIdent = "Z12345"
        coEvery { texasClient.introspectToken(any(), IdentityProvider.AZUREAD) } returns TexasIntrospectionResponse(
            active = true,
            error = null,
            groups = listOf("1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"),
            roles = null,
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "preferred_username" to epost,
                "NAVident" to navIdent,
            ),
        )
        runTest {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        register(ContentType.Application.Json, JacksonConverter(objectMapper))
                    }
                    authentication {
                        register(
                            TexasAuthenticationProvider(
                                TexasAuthenticationProvider.Config(
                                    name = IdentityProvider.AZUREAD.value,
                                    texasClient = texasClient,
                                    identityProvider = IdentityProvider.AZUREAD,
                                ),
                            ),
                        )
                    }
                    routing {
                        authenticate(IdentityProvider.AZUREAD.value) {
                            get("/some-path") {
                                val saksbehandler = call.saksbehandler(alleAdRoller)
                                    ?: throw RuntimeException("Kunne ikke mappe til saksbehandler")
                                call.respond(message = saksbehandler, status = HttpStatusCode.OK)
                            }
                        }
                    }
                }
                defaultRequest(
                    HttpMethod.Get,
                    url {
                        protocol = URLProtocol.HTTPS
                        path("/some-path")
                    },
                    jwt = jwtGenerator.createJwtForSaksbehandler(),
                ).apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe """{"navIdent":"$navIdent","brukernavn":"Sak Behandler","epost":"$epost","roller":["SAKSBEHANDLER"],"scopes":[],"klientId":"saksbehandling-id","klientnavn":"saksbehandling"}"""
                }
            }
        }
    }

    @Test
    fun `systembruker, gyldig token - autentiseres og riktig principal`() {
        coEvery { texasClient.introspectToken(any(), IdentityProvider.AZUREAD) } returns TexasIntrospectionResponse(
            active = true,
            error = null,
            groups = null,
            roles = listOf(TestSystembrukerrolle.HENTE_DATA.name),
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "idtyp" to "app",
            ),
        )
        runTest {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        register(ContentType.Application.Json, JacksonConverter(objectMapper))
                    }
                    authentication {
                        register(
                            TexasAuthenticationProvider(
                                TexasAuthenticationProvider.Config(
                                    name = IdentityProvider.AZUREAD.value,
                                    texasClient = texasClient,
                                    identityProvider = IdentityProvider.AZUREAD,
                                ),
                            ),
                        )
                    }
                    routing {
                        authenticate(IdentityProvider.AZUREAD.value) {
                            get("/some-path") {
                                val systembruker = call.systembruker(systembrukerMapper = testSystembrukerMapper)
                                    ?: throw RuntimeException("Kunne ikke mappe til systembruker")
                                call.respond(message = systembruker, status = HttpStatusCode.OK)
                            }
                        }
                    }
                }
                defaultRequest(
                    HttpMethod.Get,
                    url {
                        protocol = URLProtocol.HTTPS
                        path("/some-path")
                    },
                    jwt = jwtGenerator.createJwtForSystembruker(),
                ).apply {
                    status shouldBe HttpStatusCode.OK
                    bodyAsText() shouldBe """{"roller":["HENTE_DATA"],"klientId":"saksbehandling-id","klientnavn":"saksbehandling","navIdent":null}"""
                }
            }
        }
    }

    @Test
    fun `ekstern bruker, ugyldig token - returnerer 401`() {
        val fnr = "12345678910"
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns TexasIntrospectionResponse(
            active = false,
            error = "Expired",
            groups = null,
            roles = null,
            other = emptyMap(),
        )
        runTest {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        register(ContentType.Application.Json, JacksonConverter(objectMapper))
                    }
                    authentication {
                        register(
                            TexasAuthenticationProvider(
                                TexasAuthenticationProvider.Config(
                                    name = IdentityProvider.TOKENX.value,
                                    texasClient = texasClient,
                                    identityProvider = IdentityProvider.TOKENX,
                                ),
                            ),
                        )
                    }
                    routing {
                        authenticate(IdentityProvider.TOKENX.value) {
                            get("/some-path") {
                                val fnr = call.fnr()
                                call.respond(message = fnr, status = HttpStatusCode.OK)
                            }
                        }
                    }
                }
                defaultRequest(
                    HttpMethod.Get,
                    url {
                        protocol = URLProtocol.HTTPS
                        path("/some-path")
                    },
                    jwt = jwtGenerator.createJwtForUser(fnr = fnr),
                ).apply {
                    status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }

    @Test
    fun `ingen Authorization-header - returnerer 401`() = runTest {
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.Get, "/some-path", jwt = null).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `Authorization-header med annet skjema enn Bearer - returnerer 401`() = runTest {
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.Get, "/some-path", jwt = null) {
                headers.append(HttpHeaders.Authorization, "Basic ${jwtGenerator.createJwtForSaksbehandler()}")
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `Authorization-header som ikke er en enkelt blob - returnerer 401`() = runTest {
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.Get, "/some-path", jwt = null) {
                headers.append(HttpHeaders.Authorization, """Digest realm="tpts", nonce="abc"""")
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `introspeksjonskallet feiler - returnerer 401`() = runTest {
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } throws RuntimeException("Texas er nede")
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.Get, "/some-path").status shouldBe HttpStatusCode.Unauthorized
        }
        // Exceptions uten melding faller tilbake på en fast tekst i challengen.
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } throws RuntimeException()
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.Get, "/some-path").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `maskinporten og idporten er ikke implementert - returnerer 401`() = runTest {
        coEvery { texasClient.introspectToken(any(), any()) } returns aktivIntrospeksjon()
        testApplication {
            appMedTexasAuth(IdentityProvider.MASKINPORTEN)
            defaultRequest(HttpMethod.Get, "/some-path").status shouldBe HttpStatusCode.Unauthorized
        }
        testApplication {
            appMedTexasAuth(IdentityProvider.IDPORTEN)
            defaultRequest(HttpMethod.Get, "/some-path").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `ekstern bruker uten godkjent innloggingsnivå - returnerer 401`() = runTest {
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns aktivIntrospeksjon(
            other = mapOf("pid" to "12345678910", "acr" to "idporten-loa-substantial"),
        )
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.Get, "/some-path").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `ekstern bruker uten acr-claim - returnerer 401`() = runTest {
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns aktivIntrospeksjon(
            other = mapOf("pid" to "12345678910"),
        )
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.Get, "/some-path").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `ekstern bruker uten acr-claim slipper gjennom når innloggingsnivå ikke kreves`() = runTest {
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns aktivIntrospeksjon(
            other = mapOf("pid" to "12345678910"),
        )
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX, requireIdportenLevelHigh = false)
            defaultRequest(HttpMethod.Get, "/some-path").status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `ekstern bruker uten pid-claim - returnerer 500`() = runTest {
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns aktivIntrospeksjon(
            other = mapOf("acr" to "Level4"),
        )
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.Get, "/some-path").status shouldBe HttpStatusCode.InternalServerError
        }
    }

    private fun aktivIntrospeksjon(other: Map<String, Any?> = emptyMap()) = TexasIntrospectionResponse(
        active = true,
        error = null,
        groups = null,
        roles = null,
        other = other,
    )

    /**
     * Minimal app for feilveiene: ruta svarer bare 200, siden det er providern som avviser før den nås.
     */
    private fun ApplicationTestBuilder.appMedTexasAuth(
        identityProvider: IdentityProvider,
        requireIdportenLevelHigh: Boolean = true,
    ) {
        application {
            authentication {
                register(
                    TexasAuthenticationProvider(
                        TexasAuthenticationProvider.Config(
                            name = identityProvider.value,
                            texasClient = texasClient,
                            identityProvider = identityProvider,
                            requireIdportenLevelHigh = requireIdportenLevelHigh,
                        ),
                    ),
                )
            }
            routing {
                authenticate(identityProvider.value) {
                    get("/some-path") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
    }
}
