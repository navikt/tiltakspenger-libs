package no.nav.tiltakspenger.libs.ktor.test.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import org.junit.jupiter.api.Test

/**
 * Rutene under er testens egen server, og bruker derfor ktor sitt server-API.
 * Det er kallene mot dem som skal være ktor-frie, jf. [defaultRequest].
 */
internal class DefaultRequestTest {

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
            get("/auth") {
                call.respondText(call.request.headers.getAll(HttpHeaders.Authorization).orEmpty().joinToString(" | "))
            }
            get("/content-type") {
                call.respondText("contentType=${call.request.headers[HttpHeaders.ContentType]}")
            }
            get("/pdf") {
                call.respondBytes("%PDF".toByteArray(), ContentType.Application.Pdf)
            }
            post("/ekko") {
                call.respondText(call.receiveText(), ContentType.Application.Json)
            }
            post("/ekko-content-type") {
                call.respondText("${call.request.headers[HttpHeaders.ContentType]}: ${call.receiveText()}")
            }
            put("/metode") {
                call.respondText("PUT")
            }
            patch("/metode") {
                call.respondText("PATCH")
            }
        }
    }

    @Test
    fun `asserter status, eksakt body og content type`() {
        testApplication {
            testRoutes()
            val respons = defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/json",
                jwt = "jwt-for-test",
                forventet = ForventetRespons.eksakt(200, """{"a":1,"b":"tekst"}""", "application/json"),
            )
            respons.statusCode shouldBe 200
        }
    }

    @Test
    fun `responsen bærer med seg requestens metode og sti`() {
        testApplication {
            testRoutes()
            val respons = defaultRequest(HttpMethod.GET, "https://localhost/json", jwt = "jwt-for-test")
            respons.method shouldBe HttpMethod.GET
            respons.sti shouldBe "/json"
        }
    }

    @Test
    fun `alle metodene i httpklient sin enum treffer riktig rute`() {
        testApplication {
            testRoutes()
            defaultRequest(HttpMethod.GET, "/json").statusCode shouldBe 200
            defaultRequest(HttpMethod.POST, "/ekko", body = "{}").statusCode shouldBe 200
            defaultRequest(HttpMethod.PUT, "/metode").body shouldBe "PUT"
            defaultRequest(HttpMethod.PATCH, "/metode").body shouldBe "PATCH"
        }
    }

    @Test
    fun `body leses både som tekst og som rå bytes`() {
        testApplication {
            testRoutes()
            val respons = defaultRequest(HttpMethod.GET, "/pdf", jwt = "jwt-for-test")
            respons.body shouldBe "%PDF"
            respons.bytes shouldBe "%PDF".toByteArray()
            respons.contentType shouldBe "application/pdf"
        }
    }

    @Test
    fun `body i ForventetRespons satt til null asserter kun status`() {
        testApplication {
            testRoutes()
            val respons = defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/json",
                jwt = "jwt-for-test",
                forventet = ForventetRespons(status = 200),
            )
            respons.body shouldBe """{"a":1,"b":"tekst"}"""
        }
    }

    @Test
    fun `Json asserter json-likhet uavhengig av nøkkelrekkefølge`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/json",
                jwt = "jwt-for-test",
                forventet = ForventetRespons.json(200, """{"b":"tekst","a":1}"""),
            )
        }
    }

    @Test
    fun `Json med forventet content type`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/json",
                jwt = "jwt-for-test",
                forventet = ForventetRespons.json(200, """{"a":1,"b":"tekst"}""", "application/json"),
            )
        }
    }

    @Test
    fun `forventet satt til null gjør ingen assertions`() {
        testApplication {
            testRoutes()
            val respons = defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/feil",
                jwt = "jwt-for-test",
                forventet = null,
            )
            respons.statusCode shouldBe 500
        }
    }

    @Test
    fun `Tom godtar tom body uten content type`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/tom",
                jwt = "jwt-for-test",
                forventet = ForventetRespons.tom(200),
            )
        }
    }

    @Test
    fun `Tom feiler når responsen har body og content type`() {
        testApplication {
            testRoutes()
            shouldThrow<AssertionError> {
                defaultRequestWithAssertions(
                    method = HttpMethod.GET,
                    uri = "/json",
                    jwt = "jwt-for-test",
                    forventet = ForventetRespons(status = 200, body = ForventetBody.Tom),
                )
            }.message shouldContain "Response details:"
        }
    }

    @Test
    fun `Tom kan ikke kombineres med contentType`() {
        shouldThrow<IllegalArgumentException> {
            ForventetRespons(status = 200, body = ForventetBody.Tom, contentType = "application/json")
        }.message shouldBe "ForventetBody.Tom krever at responsen ikke har Content-Type, så contentType kan ikke settes samtidig"
    }

    @Test
    fun `status må være en tresifret statuskode`() {
        shouldThrow<IllegalArgumentException> {
            ForventetRespons(status = 20)
        }.message shouldBe "status må være en tresifret HTTP-statuskode, var 20"
        shouldThrow<IllegalArgumentException> {
            ForventetRespons(status = 1000)
        }.message shouldBe "status må være en tresifret HTTP-statuskode, var 1000"
    }

    @Test
    fun `Bytes asserter rå bytelikhet`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/pdf",
                jwt = "jwt-for-test",
                forventet = ForventetRespons.bytes(200, "%PDF".toByteArray(), "application/pdf"),
            )
        }
    }

    @Test
    fun `Bytes-avvik gir assertion-feil med byte-antall i dumpen`() {
        testApplication {
            testRoutes()
            val message = shouldThrow<AssertionError> {
                defaultRequestWithAssertions(
                    method = HttpMethod.GET,
                    uri = "/pdf",
                    jwt = "jwt-for-test",
                    forventet = ForventetRespons.bytes(200, byteArrayOf(1, 2, 3)),
                )
            }.message!!
            message shouldContain "Body: <4 byte>"
        }
    }

    @Test
    fun `feil status gir assertion-feil med respons-dump`() {
        testApplication {
            testRoutes()
            val message = shouldThrow<AssertionError> {
                defaultRequestWithAssertions(
                    method = HttpMethod.GET,
                    uri = "/json",
                    jwt = "jwt-for-test",
                    forventet = ForventetRespons(status = 400),
                )
            }.message!!
            message shouldContain "Response details:"
            message shouldContain "Status: 200"
            message shouldContain """Body: {"a":1,"b":"tekst"}"""
        }
    }

    @Test
    fun `default-jwt genereres og sendes som bearer-token`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/headere",
                forventet = ForventetRespons.eksakt(
                    200,
                    "harAuth=true, callId=DEFAULT_CALL_ID",
                    "text/plain; charset=UTF-8",
                ),
            )
        }
    }

    @Test
    fun `jwt satt til null gir request uten authorization-header`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/headere",
                jwt = null,
                forventet = ForventetRespons.eksakt(
                    200,
                    "harAuth=false, callId=DEFAULT_CALL_ID",
                    "text/plain; charset=UTF-8",
                ),
            )
        }
    }

    @Test
    fun `defaultRequest uten valgfrie argumenter sender default-jwt og call-id`() {
        testApplication {
            testRoutes()
            defaultRequest(HttpMethod.GET, "/headere").statusCode shouldBe 200
        }
    }

    @Test
    fun `headere overstyrer standardheaderne i stedet for å legge seg ved siden av`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/auth",
                jwt = "jwt-for-test",
                headere = mapOf(HttpHeaders.Authorization to "Basic tulletoken"),
                forventet = ForventetRespons.eksakt(200, "Basic tulletoken"),
            )
        }
    }

    @Test
    fun `headere kan sette authorization når jwt er null`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/auth",
                jwt = null,
                headere = mapOf(HttpHeaders.Authorization to """Digest realm="tpts""""),
                forventet = ForventetRespons.eksakt(200, """Digest realm="tpts""""),
            )
        }
    }

    @Test
    fun `request uten body sendes uten content-type`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.GET,
                uri = "/content-type",
                jwt = "jwt-for-test",
                forventet = ForventetRespons.eksakt(200, "contentType=null"),
            )
        }
    }

    @Test
    fun `bodyContentType styrer content-type for bodyen`() {
        val fnr = FnrGenerator().generer().verdi
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.POST,
                uri = "/ekko-content-type",
                jwt = "jwt-for-test",
                body = "fnr=$fnr&fom=2024-01-01",
                bodyContentType = "application/x-www-form-urlencoded",
                forventet = ForventetRespons.eksakt(
                    200,
                    "application/x-www-form-urlencoded: fnr=$fnr&fom=2024-01-01",
                ),
            )
        }
    }

    @Test
    fun `bodyContentType kan ikke være blank`() {
        testApplication {
            shouldThrow<IllegalArgumentException> {
                defaultRequest(HttpMethod.POST, "/ekko", body = "{}", bodyContentType = " ")
            }.message shouldBe "bodyContentType kan ikke være blank — den blir Content-Type-headeren på requesten."
        }
    }

    @Test
    fun `body sendes som request-body`() {
        testApplication {
            testRoutes()
            defaultRequestWithAssertions(
                method = HttpMethod.POST,
                uri = "/ekko",
                jwt = "jwt-for-test",
                body = """{"x":42}""",
                forventet = ForventetRespons.json(200, """{"x":42}""", "application/json"),
            )
        }
    }
}
