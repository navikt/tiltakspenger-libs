package no.nav.tiltakspenger.libs.ktor.test.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

internal class DefaultRequestWithAssertionsTest {

    private fun ApplicationTestBuilder.testRoutes() {
        routing {
            get("/json") {
                call.respondText("""{"a":1,"b":"tekst"}""", ContentType.Application.Json)
            }
            get("/tom") {
                call.respond(HttpStatusCode.OK)
            }
            get("/headere") {
                call.respondText(
                    "harAuth=${call.request.headers[HttpHeaders.Authorization] != null}, callId=${call.request.headers[HttpHeaders.XCorrelationId]}",
                )
            }
            post("/ekko") {
                call.respondText(call.receiveText(), ContentType.Application.Json)
            }
        }
    }

    @Test
    fun `asserter status, eksakt body og content type`() {
        testApplication {
            testRoutes()
            val response = defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/json",
                jwt = "jwt-for-test",
                forventetStatus = HttpStatusCode.OK,
                forventetBody = """{"a":1,"b":"tekst"}""",
                forventetContentType = ContentType.Application.Json,
            )
            response.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `forventetJsonBody asserter json-likhet uavhengig av nøkkelrekkefølge`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/json",
                jwt = "jwt-for-test",
                forventetStatus = HttpStatusCode.OK,
                forventetJsonBody = """{"b":"tekst","a":1}""",
            )
        }
    }

    @Test
    fun `kaster når både forventetBody og forventetJsonBody er satt`() {
        testApplication {
            testRoutes()
            shouldThrow<IllegalArgumentException> {
                defaultRequestWithAssertions(
                    method = HttpMethod.Get,
                    uri = "/json",
                    jwt = "jwt-for-test",
                    forventetStatus = HttpStatusCode.OK,
                    forventetBody = """{"a":1,"b":"tekst"}""",
                    forventetJsonBody = """{"a":1,"b":"tekst"}""",
                )
            }.message shouldBe "Sett maks én av forventetBody og forventetJsonBody"
        }
    }

    @Test
    fun `godtar tom body uten content type`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/tom",
                jwt = "jwt-for-test",
                forventetStatus = HttpStatusCode.OK,
                forventetBody = "",
            )
        }
    }

    @Test
    fun `tom forventet body krever at responsen ikke har content type`() {
        testApplication {
            testRoutes()
            shouldThrow<AssertionError> {
                defaultRequestWithAssertions(
                    method = HttpMethod.Get,
                    uri = "/json",
                    jwt = "jwt-for-test",
                    forventetStatus = HttpStatusCode.OK,
                    forventetBody = "",
                )
            }.message shouldContain "Response details:"
        }
    }

    @Test
    fun `feil status gir assertion-feil med respons-dump`() {
        testApplication {
            testRoutes()
            val message = shouldThrow<AssertionError> {
                defaultRequestWithAssertions(
                    method = HttpMethod.Get,
                    uri = "/json",
                    jwt = "jwt-for-test",
                    forventetStatus = HttpStatusCode.BadRequest,
                )
            }.message!!
            message shouldContain "Response details:"
            message shouldContain "Status: 200 OK"
            message shouldContain """Body: {"a":1,"b":"tekst"}"""
        }
    }

    @Test
    fun `default-jwt genereres og sendes som bearer-token`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/headere",
                forventetStatus = HttpStatusCode.OK,
                forventetBody = "harAuth=true, callId=DEFAULT_CALL_ID",
                forventetContentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8),
            )
        }
    }

    @Test
    fun `jwt satt til null gir request uten authorization-header`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/headere",
                jwt = null,
                forventetStatus = HttpStatusCode.OK,
                forventetBody = "harAuth=false, callId=DEFAULT_CALL_ID",
                forventetContentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8),
            )
        }
    }

    @Test
    fun `defaultRequest uten valgfrie argumenter sender default-jwt og call-id`() {
        testApplication {
            testRoutes()
            val response = defaultRequest(HttpMethod.Get, "/headere")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `setup kan sette request-body`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.Post,
                uri = "/ekko",
                jwt = "jwt-for-test",
                forventetStatus = HttpStatusCode.OK,
                forventetJsonBody = """{"x":42}""",
                forventetContentType = ContentType.Application.Json,
            ) {
                setBody("""{"x":42}""")
            }
        }
    }
}
