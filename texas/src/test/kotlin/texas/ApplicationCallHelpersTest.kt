package no.nav.tiltakspenger.libs.texas

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.ktor.test.common.ForventetRespons
import no.nav.tiltakspenger.libs.ktor.test.common.defaultRequest
import no.nav.tiltakspenger.libs.ktor.test.common.defaultRequestWithAssertions
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import org.junit.jupiter.api.Test

/** Rollene testene autoriserer mot; immutable og derfor trygg å dele på tvers av tester. */
private val alleAdRoller = listOf(
    AdRolle(Saksbehandlerrolle.SAKSBEHANDLER, "1b3a2c4d-d620-4fcf-a29b-a6cdadf29680"),
)

/**
 * Dekker feilveiene i `call.fnr()`, `call.saksbehandler()` og `call.systembruker()`.
 * Suksessveiene dekkes av [TexasAuthenticationProviderTest], som kjører samme rutene gjennom hele auth-providern.
 */
internal class ApplicationCallHelpersTest {

    /**
     * Mocken og de fangede feilene bygges per test via [medKontekst], slik at ingenting deles når testmetoder kjører parallelt med `per_class`-livssyklus.
     */
    private class Testkontekst {
        val texasClient = mockk<TexasClient>()
        val kastedeFeil = mutableListOf<Throwable>()

        fun introspeksjonGir(
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
        fun kastedeFeilErManglerPrincipal() {
            kastedeFeil.map { it.shouldBeInstanceOf<IllegalStateException>().message }.distinct() shouldBe listOf("Mangler principal")
        }

        /**
         * Test-motoren gjør ubehandlede exceptions om til 500 i stedet for å kaste dem videre til klienten.
         * Vi fanger dem i pipelinen slik at testene kan asserte på selve feilen og ikke bare statuskoden.
         */
        fun Application.fangKastedeFeil() {
            intercept(ApplicationCallPipeline.Setup) {
                try {
                    proceed()
                } catch (e: Throwable) {
                    kastedeFeil += e
                    throw e
                }
            }
        }

        fun ApplicationTestBuilder.texasApp() {
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

    private fun medKontekst(block: suspend Testkontekst.() -> Unit) = runTest { Testkontekst().block() }

    @Test
    fun `saksbehandler - systembrukertoken gir 403 ikke_saksbehandler`() = medKontekst {
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
                method = HttpMethod.GET,
                uri = "/saksbehandler",
                forventet = ForventetRespons.json(403, """{"melding":"Brukeren er ikke en saksbehandler","kode":"ikke_saksbehandler"}"""),
            )
        }
    }

    @Test
    fun `saksbehandler - ingen grupper gir 403 mangler_rolle`() = medKontekst {
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
                method = HttpMethod.GET,
                uri = "/saksbehandler",
                forventet = ForventetRespons.json(403, """{"melding":"Saksbehandler må ha minst en autorisert rolle for å aksessere denne ressursen","kode":"mangler_rolle"}"""),
            )
        }
    }

    @Test
    fun `saksbehandler - manglende NAVident gir 403 ugyldig_token`() = medKontekst {
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
                method = HttpMethod.GET,
                uri = "/saksbehandler",
                forventet = ForventetRespons.json(403, """{"melding":"Tokenet mangler claim: NAVident","kode":"ugyldig_token"}"""),
            )
        }
    }

    @Test
    fun `systembruker - saksbehandlertoken gir 403 ikke_systembruker`() = medKontekst {
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
                method = HttpMethod.GET,
                uri = "/systembruker",
                forventet = ForventetRespons.json(403, """{"melding":"Brukeren er ikke en systembruker","kode":"ikke_systembruker"}"""),
            )
        }
    }

    @Test
    fun `systembruker - ingen roller gir 403 mangler_rolle`() = medKontekst {
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
                method = HttpMethod.GET,
                uri = "/systembruker",
                forventet = ForventetRespons.json(403, """{"melding":"Systembrukeren må ha minst en autorisert rolle for å aksessere denne ressursen","kode":"mangler_rolle"}"""),
            )
        }
    }

    @Test
    fun `systembruker - manglende azp_name gir 403 ugyldig_token`() = medKontekst {
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
                method = HttpMethod.GET,
                uri = "/systembruker",
                forventet = ForventetRespons.json(403, """{"melding":"Tokenet mangler claim: azp_name","kode":"ugyldig_token"}"""),
            )
        }
    }

    @Test
    fun `fnr - intern principal på ruta gir IllegalStateException`() = medKontekst {
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
            defaultRequest(method = HttpMethod.GET, uri = "/fnr").statusCode shouldBe 500
        }
        kastedeFeilErManglerPrincipal()
    }

    @Test
    fun `saksbehandler og systembruker uten intern principal gir IllegalStateException`() = medKontekst {
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
            defaultRequest(method = HttpMethod.GET, uri = "/saksbehandler-uten-auth").statusCode shouldBe 500
            kastedeFeilErManglerPrincipal()
            kastedeFeil.clear()

            defaultRequest(method = HttpMethod.GET, uri = "/systembruker-uten-auth").statusCode shouldBe 500
            kastedeFeilErManglerPrincipal()
        }
    }

    /**
     * Mappingfeilen som ikke kan oppstå i sin egen retning svarer 500.
     * Den nås kun ved å kalle oversetteren direkte, siden `toSaksbehandler`/`toSystembruker` aldri returnerer motpartens variant.
     */
    @Test
    fun `mappingfeil fra motsatt retning gir 500 ukjent_feil`() = medKontekst {
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
                method = HttpMethod.GET,
                uri = "/saksbehandler-ukjent-feil",
                forventet = ForventetRespons.json(500, """{"melding":"Noe gikk galt ved mapping til saksbehandler","kode":"ukjent_feil"}"""),
            )
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/systembruker-ukjent-feil",
                forventet = ForventetRespons.json(500, """{"melding":"Noe gikk galt ved mapping til systembruker","kode":"ukjent_feil"}"""),
            )
        }
    }
}
