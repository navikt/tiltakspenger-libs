package no.nav.tiltakspenger.libs.ktor.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import no.nav.tiltakspenger.libs.common.MeldekortId
import no.nav.tiltakspenger.libs.common.RammebehandlingId
import no.nav.tiltakspenger.libs.common.SakId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.VedtakId
import org.junit.jupiter.api.Test

internal class ApplicationCallParseParamExTest {

    @Test
    fun `parseBody returns Right with valid body`() {
        testApplication {
            routing {
                post("/test") {
                    call.parseBody<TestRequestBody>().fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { body -> call.respondJsonString(json = """{"name":"${body.name}","age":${body.age}}""") },
                    )
                }
            }

            val response = client.post("/test") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Test","age":25}""")
            }

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"name":"Test","age":25}"""
        }
    }

    @Test
    fun `parseBody returns Left when body is invalid`() {
        testApplication {
            routing {
                post("/test") {
                    call.parseBody<TestRequestBody>().fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { body -> call.respondJsonString(json = """{"name":"${body.name}"}""") },
                    )
                }
            }

            val response = client.post("/test") {
                contentType(ContentType.Application.Json)
                setBody("not json")
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Kunne ikke deserialisere request","kode":"ugyldig_request"}"""
        }
    }

    @Test
    fun `parseParam returns Right with valid parameter`() {
        testApplication {
            routing {
                get("/test/{value}") {
                    call.parseParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ).fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { value -> call.respondJsonString(json = """{"value":$value}""") },
                    )
                }
            }

            val response = client.get("/test/42")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":42}"""
        }
    }

    @Test
    fun `parseParam returns Left when parameter is missing`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Missing number",
                        errorCode = "missing_number",
                    ).fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { value -> call.respondJsonString(json = """{"value":$value}""") },
                    )
                }
            }

            val response = client.get("/test")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Missing number","kode":"missing_number"}"""
        }
    }

    @Test
    fun `parseParam returns Left when parsing fails`() {
        testApplication {
            routing {
                get("/test/{value}") {
                    call.parseParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ).fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { value -> call.respondJsonString(json = """{"value":$value}""") },
                    )
                }
            }

            val response = client.get("/test/not-a-number")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
        }
    }

    @Test
    fun `parseSakId returns Right with valid sakId`() {
        testApplication {
            val sakId = SakId.random()
            routing {
                get("/test/{sakId}") {
                    call.parseSakId().fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { id -> call.respondJsonString(json = """{"sakId":"$id"}""") },
                    )
                }
            }

            val response = client.get("/test/$sakId")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"sakId":"$sakId"}"""
        }
    }

    @Test
    fun `parseSakId returns Left with invalid sakId`() {
        testApplication {
            routing {
                get("/test/{sakId}") {
                    call.parseSakId().fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { id -> call.respondJsonString(json = """{"sakId":"$id"}""") },
                    )
                }
            }

            val response = client.get("/test/invalid-uuid")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Ugyldig sak id","kode":"ugyldig_sak_id"}"""
        }
    }

    @Test
    fun `parseSøknadId returns Right with valid søknadId`() {
        testApplication {
            val søknadId = SøknadId.random()
            routing {
                get("/test/{søknadId}") {
                    call.parseSøknadId().fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { id -> call.respondJsonString(json = """{"søknadId":"$id"}""") },
                    )
                }
            }

            val response = client.get("/test/$søknadId")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"søknadId":"$søknadId"}"""
        }
    }

    @Test
    fun `parseRammebehandlingId returns Right with valid behandlingId`() {
        testApplication {
            val behandlingId = RammebehandlingId.random()
            routing {
                get("/test/{behandlingId}") {
                    call.parseRammebehandlingId().fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { id -> call.respondJsonString(json = """{"behandlingId":"$id"}""") },
                    )
                }
            }

            val response = client.get("/test/$behandlingId")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"behandlingId":"$behandlingId"}"""
        }
    }

    @Test
    fun `parseMeldekortId returns Right with valid meldekortId`() {
        testApplication {
            val meldekortId = MeldekortId.random()
            routing {
                get("/test/{meldekortId}") {
                    call.parseMeldekortId().fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { id -> call.respondJsonString(json = """{"meldekortId":"$id"}""") },
                    )
                }
            }

            val response = client.get("/test/$meldekortId")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"meldekortId":"$meldekortId"}"""
        }
    }

    @Test
    fun `parseVedtakId returns Right with valid vedtakId`() {
        testApplication {
            val vedtakId = VedtakId.random()
            routing {
                get("/test/{vedtakId}") {
                    call.parseVedtakId().fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { id -> call.respondJsonString(json = """{"vedtakId":"$id"}""") },
                    )
                }
            }

            val response = client.get("/test/$vedtakId")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"vedtakId":"$vedtakId"}"""
        }
    }

    private data class TestRequestBody(
        val name: String,
        val age: Int,
    )
}
