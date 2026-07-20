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
            get("/feil") {
                call.respondText("noe gikk galt", status = HttpStatusCode.InternalServerError)
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
                forventet = ForventetRespons(
                    status = HttpStatusCode.OK,
                    body = ForventetBody.Eksakt("""{"a":1,"b":"tekst"}"""),
                    contentType = ContentType.Application.Json,
                ),
            )
            response.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `Json asserter json-likhet uavhengig av nøkkelrekkefølge`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/json",
                jwt = "jwt-for-test",
                forventet = ForventetRespons(
                    status = HttpStatusCode.OK,
                    body = ForventetBody.Json("""{"b":"tekst","a":1}"""),
                ),
            )
        }
    }

    @Test
    fun `forventet satt til null gjør ingen assertions`() {
        testApplication {
            testRoutes()
            val response = defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/feil",
                jwt = "jwt-for-test",
                forventet = null,
            )
            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `Tom godtar tom body uten content type`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.Get,
                uri = "/tom",
                jwt = "jwt-for-test",
                forventet = ForventetRespons(
                    status = HttpStatusCode.OK,
                    body = ForventetBody.Tom,
                ),
            )
        }
    }

    @Test
    fun `Tom feiler når responsen har body og content type`() {
        testApplication {
            testRoutes()
            shouldThrow<AssertionError> {
                defaultRequestWithAssertions(
                    method = HttpMethod.Get,
                    uri = "/json",
                    jwt = "jwt-for-test",
                    forventet = ForventetRespons(
                        status = HttpStatusCode.OK,
                        body = ForventetBody.Tom,
                    ),
                )
            }.message shouldContain "Response details:"
        }
    }

    @Test
    fun `Tom kan ikke kombineres med contentType`() {
        shouldThrow<IllegalArgumentException> {
            ForventetRespons(
                status = HttpStatusCode.OK,
                body = ForventetBody.Tom,
                contentType = ContentType.Application.Json,
            )
        }.message shouldBe "ForventetBody.Tom krever at responsen ikke har Content-Type, så contentType kan ikke settes samtidig"
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
                    forventet = ForventetRespons(status = HttpStatusCode.BadRequest),
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
                forventet = ForventetRespons(
                    status = HttpStatusCode.OK,
                    body = ForventetBody.Eksakt("harAuth=true, callId=DEFAULT_CALL_ID"),
                    contentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                ),
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
                forventet = ForventetRespons(
                    status = HttpStatusCode.OK,
                    body = ForventetBody.Eksakt("harAuth=false, callId=DEFAULT_CALL_ID"),
                    contentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                ),
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
                forventet = ForventetRespons(
                    status = HttpStatusCode.OK,
                    body = ForventetBody.Json("""{"x":42}"""),
                    contentType = ContentType.Application.Json,
                ),
            ) {
                setBody("""{"x":42}""")
            }
        }
    }
}
