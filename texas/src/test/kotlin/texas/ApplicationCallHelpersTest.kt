package no.nav.tiltakspenger.libs.texas

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.Saksbehandlerrolle
import no.nav.tiltakspenger.libs.ktor.test.common.ForventetBody
import no.nav.tiltakspenger.libs.ktor.test.common.ForventetRespons
import no.nav.tiltakspenger.libs.ktor.test.common.defaultRequest
import no.nav.tiltakspenger.libs.ktor.test.common.defaultRequestWithAssertions
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Dekker feilveiene i `call.fnr()`, `call.saksbehandler()` og `call.systembruker()`.
 * Suksessveiene dekkes av [TexasAuthenticationProviderTest], som kjører samme rutene gjennom hele auth-providern.
 */
internal class ApplicationCallHelpersTest {
    private val texasClient = mockk<TexasClient>()
    private val alleAdRoller = listOf(
        AdRolle(Saksbehandlerrolle.SAKSBEHANDLER, "1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"),
    )
    private val kastedeFeil = mutableListOf<Throwable>()

    /**
     * Testklassen kjører med `per_class`-livssyklus (se conventionpluginet), så instansen deles av alle testene her.
     * Uten denne ville [kastedeFeil] akkumulert på tvers, og assertionene kunne ikke sagt noe om antall.
     */
    @BeforeEach
    fun tømFangedeFeil() = kastedeFeil.clear()

    @Test
    fun `saksbehandler - systembrukertoken gir 403 ikke_saksbehandler`() = runTest {
        introspeksjonGir(
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "idtyp" to "app",
            ),
            roller = listOf(TestSystembrukerrolle.HENTE_DATA.name),
        )

        testApplication {
            texasApp()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/saksbehandler",
                forventet = ForventetRespons(
                    status = HttpStatusCode.Forbidden,
                    body = ForventetBody.Json("""{"melding":"Brukeren er ikke en saksbehandler","kode":"ikke_saksbehandler"}"""),
                ),
            )
        }
    }

    @Test
    fun `saksbehandler - ingen grupper gir 403 mangler_rolle`() = runTest {
        introspeksjonGir(
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "NAVident" to "Z12345",
                "preferred_username" to "Sak.Behandler@nav.no",
            ),
            grupper = emptyList(),
        )

        testApplication {
            texasApp()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/saksbehandler",
                forventet = ForventetRespons(
                    status = HttpStatusCode.Forbidden,
                    body = ForventetBody.Json("""{"melding":"Saksbehandler må ha minst en autorisert rolle for å aksessere denne ressursen","kode":"mangler_rolle"}"""),
                ),
            )
        }
    }

    @Test
    fun `saksbehandler - manglende NAVident gir 403 ugyldig_token`() = runTest {
        introspeksjonGir(
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "preferred_username" to "Sak.Behandler@nav.no",
            ),
            grupper = listOf("1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"),
        )

        testApplication {
            texasApp()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/saksbehandler",
                forventet = ForventetRespons(
                    status = HttpStatusCode.Forbidden,
                    body = ForventetBody.Json("""{"melding":"Tokenet mangler claim: NAVident","kode":"ugyldig_token"}"""),
                ),
            )
        }
    }

    @Test
    fun `systembruker - saksbehandlertoken gir 403 ikke_systembruker`() = runTest {
        introspeksjonGir(
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "NAVident" to "Z12345",
                "preferred_username" to "Sak.Behandler@nav.no",
            ),
            grupper = listOf("1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"),
        )

        testApplication {
            texasApp()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/systembruker",
                forventet = ForventetRespons(
                    status = HttpStatusCode.Forbidden,
                    body = ForventetBody.Json("""{"melding":"Brukeren er ikke en systembruker","kode":"ikke_systembruker"}"""),
                ),
            )
        }
    }

    @Test
    fun `systembruker - ingen roller gir 403 mangler_rolle`() = runTest {
        introspeksjonGir(
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "idtyp" to "app",
            ),
            roller = emptyList(),
        )

        testApplication {
            texasApp()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/systembruker",
                forventet = ForventetRespons(
                    status = HttpStatusCode.Forbidden,
                    body = ForventetBody.Json("""{"melding":"Systembrukeren må ha minst en autorisert rolle for å aksessere denne ressursen","kode":"mangler_rolle"}"""),
                ),
            )
        }
    }

    @Test
    fun `systembruker - manglende azp_name gir 403 ugyldig_token`() = runTest {
        introspeksjonGir(
            other = mapOf(
                "azp" to "saksbehandling-id",
                "idtyp" to "app",
            ),
            roller = listOf(TestSystembrukerrolle.HENTE_DATA.name),
        )

        testApplication {
            texasApp()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/systembruker",
                forventet = ForventetRespons(
                    status = HttpStatusCode.Forbidden,
                    body = ForventetBody.Json("""{"melding":"Tokenet mangler claim: azp_name","kode":"ugyldig_token"}"""),
                ),
            )
        }
    }

    @Test
    fun `fnr - intern principal på ruta gir IllegalStateException`() = runTest {
        introspeksjonGir(
            other = mapOf(
                "azp_name" to "saksbehandling",
                "azp" to "saksbehandling-id",
                "idtyp" to "app",
            ),
            roller = listOf(TestSystembrukerrolle.HENTE_DATA.name),
        )

        testApplication {
            texasApp()
            defaultRequest(method = HttpMethod.Get, uri = "/fnr").status shouldBe HttpStatusCode.InternalServerError
        }
        kastedeFeilErManglerPrincipal()
    }

    @Test
    fun `saksbehandler og systembruker uten intern principal gir IllegalStateException`() = runTest {
        testApplication {
            application {
                fangKastedeFeil()
                routing {
                    get("/saksbehandler-uten-auth") {
                        call.saksbehandler(alleAdRoller)
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/systembruker-uten-auth") {
                        call.systembruker(testSystembrukerMapper)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            // Rutene sjekkes hver for seg: samles feilene opp, ville testen bestått selv om bare den ene ruta kastet.
            defaultRequest(method = HttpMethod.Get, uri = "/saksbehandler-uten-auth").status shouldBe HttpStatusCode.InternalServerError
            kastedeFeilErManglerPrincipal()
            kastedeFeil.clear()

            defaultRequest(method = HttpMethod.Get, uri = "/systembruker-uten-auth").status shouldBe HttpStatusCode.InternalServerError
            kastedeFeilErManglerPrincipal()
        }
    }

    /**
     * Mappingfeilen som ikke kan oppstå i sin egen retning svarer 500.
     * Den nås kun ved å kalle oversetteren direkte, siden `toSaksbehandler`/`toSystembruker` aldri returnerer motpartens variant.
     */
    @Test
    fun `mappingfeil fra motsatt retning gir 500 ukjent_feil`() = runTest {
        testApplication {
            application {
                routing {
                    get("/saksbehandler-ukjent-feil") {
                        call.respondMappingfeilForSaksbehandler(InternalPrincipalMappingfeil.IkkeSystembruker)
                    }
                    get("/systembruker-ukjent-feil") {
                        call.respondMappingfeilForSystembruker(InternalPrincipalMappingfeil.IkkeSaksbehandler)
                    }
                }
            }
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/saksbehandler-ukjent-feil",
                forventet = ForventetRespons(
                    status = HttpStatusCode.InternalServerError,
                    body = ForventetBody.Json("""{"melding":"Noe gikk galt ved mapping til saksbehandler","kode":"ukjent_feil"}"""),
                ),
            )
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/systembruker-ukjent-feil",
                forventet = ForventetRespons(
                    status = HttpStatusCode.InternalServerError,
                    body = ForventetBody.Json("""{"melding":"Noe gikk galt ved mapping til systembruker","kode":"ukjent_feil"}"""),
                ),
            )
        }
    }

    private fun introspeksjonGir(
        other: Map<String, Any?>,
        grupper: List<String>? = null,
        roller: List<String>? = null,
    ) {
        coEvery { texasClient.introspectToken(any(), IdentityProvider.AZUREAD) } returns TexasIntrospectionResponse(
            active = true,
            error = null,
            groups = grupper,
            roles = roller,
            other = other,
        )
    }

    /**
     * Asserter at det ble fanget minst én feil, og at alle fangede feilene er `IllegalStateException("Mangler principal")`.
     * Samme exception passerer pipelinen flere ganger per request, så antallet er en implementasjonsdetalj i ktor og assertes bevisst ikke.
     */
    private fun kastedeFeilErManglerPrincipal() {
        kastedeFeil.map { it.shouldBeInstanceOf<IllegalStateException>().message }.distinct() shouldBe listOf("Mangler principal")
    }

    /**
     * Test-motoren gjør ubehandlede exceptions om til 500 i stedet for å kaste dem videre til klienten.
     * Vi fanger dem i pipelinen slik at testene kan asserte på selve feilen og ikke bare statuskoden.
     */
    private fun Application.fangKastedeFeil() {
        intercept(ApplicationCallPipeline.Setup) {
            try {
                proceed()
            } catch (e: Throwable) {
                kastedeFeil += e
                throw e
            }
        }
    }

    private fun ApplicationTestBuilder.texasApp() {
        application {
            fangKastedeFeil()
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
                    get("/saksbehandler") {
                        call.saksbehandler(alleAdRoller) ?: return@get
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/systembruker") {
                        call.systembruker(testSystembrukerMapper) ?: return@get
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/fnr") {
                        call.respond(call.fnr())
                    }
                }
            }
        }
    }
}
