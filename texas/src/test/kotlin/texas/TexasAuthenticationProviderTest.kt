package no.nav.tiltakspenger.libs.texas

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.auth.test.core.JwtGenerator
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.common.Saksbehandlerrolle
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.ktor.test.common.defaultRequest
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import org.junit.jupiter.api.Test

class TexasAuthenticationProviderTest {
    // Mocken bygges per test slik at stubbing ikke deles mellom parallelle tester; klokka og jwt-generatoren er tilstandsløse og trygge å dele.
    val clock = fixedClock
    val jwtGenerator = JwtGenerator(clock = clock)
    private val fnr = FnrGenerator().generer().verdi

    @Test
    fun `ekstern bruker, gyldig token - autentiseres og riktig principal`() {
        val texasClient = mockk<TexasClient>()
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
                    HttpMethod.GET,
                    "/some-path",
                    jwt = jwtGenerator.createJwtForUser(fnr = fnr),
                ).apply {
                    statusCode shouldBe 200
                    body shouldBe objectMapper.writeValueAsString(Fnr.fromString(fnr))
                }
            }
        }
    }

    @Test
    fun `saksbehandler, gyldig token - autentiseres og riktig principal`() {
        val alleAdRoller = listOf(
            AdRolle(Saksbehandlerrolle.SAKSBEHANDLER, "1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"),
            AdRolle(Saksbehandlerrolle.BESLUTTER, "79985315-b2de-40b8-a740-9510796993c6"),
        )
        val epost = "Sak.Behandler@nav.no"
        val navIdent = "Z12345"
        val texasClient = mockk<TexasClient>()
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
                    HttpMethod.GET,
                    "/some-path",
                    jwt = jwtGenerator.createJwtForSaksbehandler(),
                ).apply {
                    statusCode shouldBe 200
                    body shouldBe """{"navIdent":"Z12345","brukernavn":"Sak Behandler","epost":"Sak.Behandler@nav.no","roller":["SAKSBEHANDLER"],"scopes":[],"klientId":"saksbehandling-id","klientnavn":"saksbehandling","erBeslutter":false,"erSaksbehandler":true,"erSaksbehandlerEllerBeslutter":true}"""
                }
            }
        }
    }

    @Test
    fun `systembruker, gyldig token - autentiseres og riktig principal`() {
        val texasClient = mockk<TexasClient>()
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
                    HttpMethod.GET,
                    "/some-path",
                    jwt = jwtGenerator.createJwtForSystembruker(),
                ).apply {
                    statusCode shouldBe 200
                    body shouldBe """{"roller":["HENTE_DATA"],"klientId":"saksbehandling-id","klientnavn":"saksbehandling","navIdent":null}"""
                }
            }
        }
    }

    @Test
    fun `ekstern bruker, ugyldig token - returnerer 401`() {
        val texasClient = mockk<TexasClient>()
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
                    HttpMethod.GET,
                    "/some-path",
                    jwt = jwtGenerator.createJwtForUser(fnr = fnr),
                ).apply {
                    statusCode shouldBe 401
                }
            }
        }
    }

    @Test
    fun `ingen Authorization-header - returnerer 401`() = runTest {
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(HttpMethod.GET, "/some-path", jwt = null).statusCode shouldBe 401
        }
    }

    @Test
    fun `Authorization-header med annet skjema enn Bearer - returnerer 401`() = runTest {
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(
                HttpMethod.GET,
                "/some-path",
                jwt = null,
                headere = mapOf(HttpHeaders.Authorization to "Basic ${jwtGenerator.createJwtForSaksbehandler()}"),
            ).statusCode shouldBe 401
        }
    }

    @Test
    fun `Authorization-header som ikke er en enkelt blob - returnerer 401`() = runTest {
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX)
            defaultRequest(
                HttpMethod.GET,
                "/some-path",
                jwt = null,
                headere = mapOf(HttpHeaders.Authorization to """Digest realm="tpts", nonce="abc""""),
            ).statusCode shouldBe 401
        }
    }

    @Test
    fun `introspeksjonskallet feiler - returnerer 401`() = runTest {
        val texasClient = mockk<TexasClient>()
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } throws RuntimeException("Texas er nede")
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX, texasClient = texasClient)
            defaultRequest(HttpMethod.GET, "/some-path").statusCode shouldBe 401
        }
        // Exceptions uten melding faller tilbake på en fast tekst i challengen.
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } throws RuntimeException()
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX, texasClient = texasClient)
            defaultRequest(HttpMethod.GET, "/some-path").statusCode shouldBe 401
        }
    }

    @Test
    fun `maskinporten og idporten er ikke implementert - returnerer 401`() = runTest {
        val texasClient = mockk<TexasClient>()
        coEvery { texasClient.introspectToken(any(), any()) } returns aktivIntrospeksjon()
        testApplication {
            appMedTexasAuth(IdentityProvider.MASKINPORTEN, texasClient = texasClient)
            defaultRequest(HttpMethod.GET, "/some-path").statusCode shouldBe 401
        }
        testApplication {
            appMedTexasAuth(IdentityProvider.IDPORTEN, texasClient = texasClient)
            defaultRequest(HttpMethod.GET, "/some-path").statusCode shouldBe 401
        }
    }

    @Test
    fun `ekstern bruker uten godkjent innloggingsnivå - returnerer 401`() = runTest {
        val texasClient = mockk<TexasClient>()
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns aktivIntrospeksjon(
            other = mapOf("pid" to fnr, "acr" to "idporten-loa-substantial"),
        )
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX, texasClient = texasClient)
            defaultRequest(HttpMethod.GET, "/some-path").statusCode shouldBe 401
        }
    }

    @Test
    fun `ekstern bruker uten acr-claim - returnerer 401`() = runTest {
        val texasClient = mockk<TexasClient>()
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns aktivIntrospeksjon(
            other = mapOf("pid" to fnr),
        )
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX, texasClient = texasClient)
            defaultRequest(HttpMethod.GET, "/some-path").statusCode shouldBe 401
        }
    }

    @Test
    fun `ekstern bruker uten acr-claim slipper gjennom når innloggingsnivå ikke kreves`() = runTest {
        val texasClient = mockk<TexasClient>()
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns aktivIntrospeksjon(
            other = mapOf("pid" to fnr),
        )
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX, requireIdportenLevelHigh = false, texasClient = texasClient)
            defaultRequest(HttpMethod.GET, "/some-path").statusCode shouldBe 200
        }
    }

    @Test
    fun `ekstern bruker uten pid-claim - returnerer 500`() = runTest {
        val texasClient = mockk<TexasClient>()
        coEvery { texasClient.introspectToken(any(), IdentityProvider.TOKENX) } returns aktivIntrospeksjon(
            other = mapOf("acr" to "Level4"),
        )
        testApplication {
            appMedTexasAuth(IdentityProvider.TOKENX, texasClient = texasClient)
            defaultRequest(HttpMethod.GET, "/some-path").statusCode shouldBe 500
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
     * Standardverdien gir en ustubbet mock til testene som avvises før introspeksjonskallet; mocken lages per kall og deler ingen tilstand.
     */
    private fun ApplicationTestBuilder.appMedTexasAuth(
        identityProvider: IdentityProvider,
        requireIdportenLevelHigh: Boolean = true,
        texasClient: TexasClient = mockk(),
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
