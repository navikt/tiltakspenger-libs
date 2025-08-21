package no.nav.tiltakspenger.libs.auth.ktor

import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.util.url
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.auth.test.core.JwtGenerator
import no.nav.tiltakspenger.libs.auth.test.core.TestSystembruker
import no.nav.tiltakspenger.libs.auth.test.core.tokenServiceForTest
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.ktor.test.common.defaultRequest
import org.junit.jupiter.api.Test

internal class ApplicationCallAutentiseringExTest {

    @Test
    fun `svarer med WWW-Authenticate for systembruker`() {
        runTest {
            testApplication {
                val tokenService = tokenServiceForTest()
                application {
                    routing {
                        get("/some-path") {
                            call.withSystembruker<TestSystembruker>(tokenService = tokenService) {
                                fail("Skal ikke komme hit")
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
                    jwt = null,
                ).apply {
                    withClue(
                        "Response details:\n" +
                            "Status: ${this.status}\n" +
                            "Content-Type: ${this.contentType()}\n" +
                            "Body: ${this.bodyAsText()}\n",
                    ) {
                        status shouldBe HttpStatusCode.Unauthorized
                        headers["WWW-Authenticate"] shouldBe """Bearer realm="tiltakspenger-saksbehandling-api""""
                    }
                }
            }
        }
    }

    @Test
    fun `svarer med WWW-Authenticate for saksbehandler`() {
        runTest {
            testApplication {
                val tokenService = tokenServiceForTest()
                application {
                    routing {
                        get("/some-path") {
                            call.withSaksbehandler(tokenService = tokenService) {
                                fail("Skal ikke komme hit")
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
                    jwt = null,
                ).apply {
                    withClue(
                        "Response details:\n" +
                            "Status: ${this.status}\n" +
                            "Content-Type: ${this.contentType()}\n" +
                            "Body: ${this.bodyAsText()}\n",
                    ) {
                        status shouldBe HttpStatusCode.Unauthorized
                        headers["WWW-Authenticate"] shouldBe """Bearer realm="tiltakspenger-saksbehandling-api""""
                    }
                }
            }
        }
    }

    @Test
    fun `Brukeren er ikke saksbehandler`() {
        runTest {
            testApplication {
                val jwtGenerator = JwtGenerator()
                val tokenService = tokenServiceForTest(
                    jwtGenerator = jwtGenerator,
                )
                application {
                    this.install(ContentNegotiation) {
                        register(ContentType.Application.Json, JacksonConverter(objectMapper))
                    }
                    routing {
                        get("/some-path") {
                            call.withSaksbehandler(tokenService = tokenService) {
                                fail("Skal ikke komme hit")
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
                    withClue(
                        "Response details:\n" +
                            "Status: ${this.status}\n" +
                            "Content-Type: ${this.contentType()}\n" +
                            "Body: ${this.bodyAsText()}\n",
                    ) {
                        status shouldBe HttpStatusCode.Forbidden
                        bodyAsText() shouldBe """{"melding":"Brukeren er ikke en saksbehandler","kode":"ikke_saksbehandler"}"""
                    }
                }
            }
        }
    }
}
