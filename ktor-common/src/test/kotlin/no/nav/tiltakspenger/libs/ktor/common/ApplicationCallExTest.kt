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
import no.nav.tiltakspenger.libs.common.BehandlingId
import no.nav.tiltakspenger.libs.common.MeldekortId
import no.nav.tiltakspenger.libs.common.SakId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.VedtakId
import org.junit.jupiter.api.Test

class ApplicationCallExTest {

    @Test
    fun `withSakId returns 400 when sakId is invalid`() = testApplication {
        routing {
            get("/test/{sakId}") {
                call.withSakId { sakId ->
                    call.respondJsonString(json = """{"sakId":"$sakId"}""")
                }
            }
        }

        val response = client.get("/test/invalid-uuid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ugyldig sak id","kode":"ugyldig_sak_id"}"""
    }

    @Test
    fun `withSakId calls onRight with valid sakId`() = testApplication {
        val sakId = SakId.random()
        routing {
            get("/test/{sakId}") {
                call.withSakId { sakId ->
                    call.respondJsonString(json = """{"sakId":"$sakId"}""")
                }
            }
        }

        val response = client.get("/test/$sakId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"sakId":"$sakId"}"""
    }

    @Test
    fun `withSøknadId returns 400 when søknadId is invalid`() = testApplication {
        routing {
            get("/test/{søknadId}") {
                call.withSøknadId { søknadId ->
                    call.respondJsonString(json = """{"søknadId":"$søknadId"}""")
                }
            }
        }

        val response = client.get("/test/invalid-uuid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ugyldig søknad id","kode":"ugyldig_søknad_id"}"""
    }

    @Test
    fun `withSøknadId calls onRight with valid søknadId`() = testApplication {
        val søknadId = SøknadId.random()
        routing {
            get("/test/{søknadId}") {
                call.withSøknadId { søknadId ->
                    call.respondJsonString(json = """{"søknadId":"$søknadId"}""")
                }
            }
        }

        val response = client.get("/test/$søknadId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"søknadId":"$søknadId"}"""
    }

    @Test
    fun `withMeldekortId returns 400 when meldekortId is invalid`() = testApplication {
        routing {
            get("/test/{meldekortId}") {
                call.withMeldekortId { meldekortId ->
                    call.respondJsonString(json = """{"meldekortId":"$meldekortId"}""")
                }
            }
        }

        val response = client.get("/test/invalid-uuid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ugyldig meldekort id","kode":"ugyldig_meldekort_id"}"""
    }

    @Test
    fun `withMeldekortId calls onRight with valid meldekortId`() = testApplication {
        val meldekortId = MeldekortId.random()
        routing {
            get("/test/{meldekortId}") {
                call.withMeldekortId { meldekortId ->
                    call.respondJsonString(json = """{"meldekortId":"$meldekortId"}""")
                }
            }
        }

        val response = client.get("/test/$meldekortId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"meldekortId":"$meldekortId"}"""
    }

    @Test
    fun `withVedtakId returns 400 when vedtakId is invalid`() = testApplication {
        routing {
            get("/test/{vedtakId}") {
                call.withVedtakId { vedtakId ->
                    call.respondJsonString(json = """{"vedtakId":"$vedtakId"}""")
                }
            }
        }

        val response = client.get("/test/invalid-uuid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ugyldig vedtak id","kode":"ugyldig_vedtak_id"}"""
    }

    @Test
    fun `withVedtakId calls onSuccess with valid vedtakId`() = testApplication {
        val vedtakId = VedtakId.random()
        routing {
            get("/test/{vedtakId}") {
                call.withVedtakId { vedtakId ->
                    call.respondJsonString(json = """{"vedtakId":"$vedtakId"}""")
                }
            }
        }

        val response = client.get("/test/$vedtakId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"vedtakId":"$vedtakId"}"""
    }

    @Test
    fun `withBehandlingId returns 400 when behandlingId is invalid`() = testApplication {
        routing {
            get("/test/{behandlingId}") {
                call.withBehandlingId { behandlingId ->
                    call.respondJsonString(json = """{"behandlingId":"$behandlingId"}""")
                }
            }
        }

        val response = client.get("/test/invalid-uuid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ugyldig behandling id","kode":"ugyldig_behandling_id"}"""
    }

    @Test
    fun `withBehandlingId calls onRight with valid behandlingId`() = testApplication {
        val behandlingId = BehandlingId.random()
        routing {
            get("/test/{behandlingId}") {
                call.withBehandlingId { behandlingId ->
                    call.respondJsonString(json = """{"behandlingId":"$behandlingId"}""")
                }
            }
        }

        val response = client.get("/test/$behandlingId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"behandlingId":"$behandlingId"}"""
    }

    @Test
    fun `withBody returns 400 when body cannot be deserialized`() = testApplication {
        routing {
            post("/test") {
                call.withBody<TestRequestBody> { body ->
                    call.respondJsonString(json = """{"name":"${body.name}"}""")
                }
            }
        }

        val response = client.post("/test") {
            contentType(ContentType.Application.Json)
            setBody("""{"invalid": "json structure"}""")
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Kunne ikke deserialisere request","kode":"ugyldig_request"}"""
    }

    @Test
    fun `withBody returns 400 when body is not valid json`() = testApplication {
        routing {
            post("/test") {
                call.withBody<TestRequestBody> { body ->
                    call.respondJsonString(json = """{"name":"${body.name}"}""")
                }
            }
        }

        val response = client.post("/test") {
            contentType(ContentType.Application.Json)
            setBody("not json at all")
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Kunne ikke deserialisere request","kode":"ugyldig_request"}"""
    }

    @Test
    fun `withBody calls ifRight with valid body`() = testApplication {
        routing {
            post("/test") {
                call.withBody<TestRequestBody> { body ->
                    call.respondJsonString(json = """{"name":"${body.name}","age":${body.age}}""")
                }
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

    @Test
    fun `respondStatus returns correct status with empty body`() = testApplication {
        routing {
            get("/test") {
                call.respondStatus(HttpStatusCode.Accepted)
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Accepted
        response.headers["Content-Type"] shouldBe "text/plain; charset=UTF-8"
        response.bodyAsText() shouldBe ""
    }

    @Test
    fun `respondOk returns 200 with empty body`() = testApplication {
        routing {
            get("/test") {
                call.respondOk()
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "text/plain; charset=UTF-8"
        response.bodyAsText() shouldBe ""
    }

    @Test
    fun `respondNoContent returns 204 with empty body`() = testApplication {
        routing {
            get("/test") {
                call.respondNoContent()
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.NoContent
        response.headers["Content-Type"] shouldBe "text/plain; charset=UTF-8"
        response.bodyAsText() shouldBe ""
    }

    @Test
    fun `respondJsonString with plain json-string`() = testApplication {
        routing {
            get("/test") {
                call.respondJsonString(json = """"my-string"""")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """"my-string""""
    }

    @Test
    fun `respondJsonString with string returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondJsonString(json = """{"key":"value"}""")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"key":"value"}"""
    }

    @Test
    fun `respondJsonString with string and custom status returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondJsonString(json = """{"key":"value"}""", HttpStatusCode.Created)
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Created
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"key":"value"}"""
    }

    @Test
    fun `respondJson with object returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondJson(value = TestResponseBody("test", 42))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"name":"test","value":42}"""
    }

    @Test
    fun `respondJson with object and custom status returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondJson(value = TestResponseBody("test", 42), HttpStatusCode.Created)
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Created
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"name":"test","value":42}"""
    }

    @Test
    fun `respondJson with Pair returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondJson(HttpStatusCode.Accepted to TestResponseBody("test", 42))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Accepted
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"name":"test","value":42}"""
    }

    @Test
    fun `respondJson with list returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondJson(listOf(TestResponseBody("a", 1), TestResponseBody("b", 2)))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """[{"name":"a","value":1},{"name":"b","value":2}]"""
    }

    @Test
    fun `respondJson with list with custom status returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondJson(listOf(TestResponseBody("a", 1)), HttpStatusCode.Created)
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Created
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """[{"name":"a","value":1}]"""
    }

    @Test
    fun `respondJson with empty list returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondJson(emptyList<TestResponseBody>())
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """[]"""
    }

    @Test
    fun `withValidParam returns 400 when parameter is missing`() = testApplication {
        routing {
            get("/test") {
                call.withValidParam(
                    paramName = "customId",
                    parse = { it },
                    errorMessage = "Custom param missing",
                    errorCode = "custom_param_missing",
                ) { value ->
                    call.respondJsonString(json = """{"value":"$value"}""")
                }
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Custom param missing","kode":"custom_param_missing"}"""
    }

    @Test
    fun `withValidParam calls onSuccess when parameter is valid`() = testApplication {
        routing {
            get("/test/{customId}") {
                call.withValidParam(
                    paramName = "customId",
                    parse = { it.toInt() },
                    errorMessage = "Invalid number",
                    errorCode = "invalid_number",
                ) { value ->
                    call.respondJsonString(json = """{"value":$value}""")
                }
            }
        }

        val response = client.get("/test/42")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"value":42}"""
    }

    @Test
    fun `withValidParam returns 400 when parsing fails`() = testApplication {
        routing {
            get("/test/{customId}") {
                call.withValidParam(
                    paramName = "customId",
                    parse = { it.toInt() },
                    errorMessage = "Invalid number",
                    errorCode = "invalid_number",
                ) { value ->
                    call.respondJsonString(json = """{"value":$value}""")
                }
            }
        }

        val response = client.get("/test/not-a-number")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
    }

    @Test
    fun `withBody with null logger works correctly`() = testApplication {
        routing {
            post("/test") {
                call.withBody<TestRequestBody>(logger = null) { body ->
                    call.respondJsonString(json = """{"name":"${body.name}","age":${body.age}}""")
                }
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

    @Test
    fun `withBody with null logger handles deserialization error correctly`() = testApplication {
        routing {
            post("/test") {
                call.withBody<TestRequestBody>(logger = null) { body ->
                    call.respondJsonString(json = """{"name":"${body.name}"}""")
                }
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

    @Test
    fun `withValidParam with null logger works correctly`() = testApplication {
        routing {
            get("/test/{value}") {
                call.withValidParam(
                    paramName = "value",
                    parse = { it.toInt() },
                    errorMessage = "Invalid number",
                    errorCode = "invalid_number",
                    logger = null,
                ) { value ->
                    call.respondJsonString(json = """{"value":$value}""")
                }
            }
        }

        val response = client.get("/test/42")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"value":42}"""
    }

    @Test
    fun `withValidParam with null logger handles parsing error correctly`() = testApplication {
        routing {
            get("/test/{value}") {
                call.withValidParam(
                    paramName = "value",
                    parse = { it.toInt() },
                    errorMessage = "Invalid number",
                    errorCode = "invalid_number",
                    logger = null,
                ) { value ->
                    call.respondJsonString(json = """{"value":$value}""")
                }
            }
        }

        val response = client.get("/test/invalid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
    }

    @Test
    fun `withBehandlingId with null logger works correctly`() = testApplication {
        val behandlingId = BehandlingId.random()
        routing {
            get("/test/{behandlingId}") {
                call.withBehandlingId(logger = null) { id ->
                    call.respondJsonString(json = """{"behandlingId":"$id"}""")
                }
            }
        }

        val response = client.get("/test/$behandlingId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"behandlingId":"$behandlingId"}"""
    }

    @Test
    fun `withSakId with null logger works correctly`() = testApplication {
        val sakId = SakId.random()
        routing {
            get("/test/{sakId}") {
                call.withSakId(logger = null) { id ->
                    call.respondJsonString(json = """{"sakId":"$id"}""")
                }
            }
        }

        val response = client.get("/test/$sakId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"sakId":"$sakId"}"""
    }

    @Test
    fun `withSøknadId with null logger works correctly`() = testApplication {
        val søknadId = SøknadId.random()
        routing {
            get("/test/{søknadId}") {
                call.withSøknadId(logger = null) { id ->
                    call.respondJsonString(json = """{"søknadId":"$id"}""")
                }
            }
        }

        val response = client.get("/test/$søknadId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"søknadId":"$søknadId"}"""
    }

    @Test
    fun `withMeldekortId with null logger works correctly`() = testApplication {
        val meldekortId = MeldekortId.random()
        routing {
            get("/test/{meldekortId}") {
                call.withMeldekortId(logger = null) { id ->
                    call.respondJsonString(json = """{"meldekortId":"$id"}""")
                }
            }
        }

        val response = client.get("/test/$meldekortId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"meldekortId":"$meldekortId"}"""
    }

    @Test
    fun `withVedtakId with null logger works correctly`() = testApplication {
        val vedtakId = VedtakId.random()
        routing {
            get("/test/{vedtakId}") {
                call.withVedtakId(logger = null) { id ->
                    call.respondJsonString(json = """{"vedtakId":"$id"}""")
                }
            }
        }

        val response = client.get("/test/$vedtakId")

        response.status shouldBe HttpStatusCode.OK
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"vedtakId":"$vedtakId"}"""
    }

    @Test
    fun `withValidParam with loggTilSikkerlogg false does not log to sikkerlogg`() = testApplication {
        routing {
            get("/test/{value}") {
                call.withValidParam(
                    paramName = "value",
                    parse = { it.toInt() },
                    errorMessage = "Invalid number",
                    errorCode = "invalid_number",
                    logger = null,
                    loggTilSikkerlogg = false,
                ) { value ->
                    call.respondJsonString(json = """{"value":$value}""")
                }
            }
        }

        val response = client.get("/test/invalid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
    }

    @Test
    fun `withValidParam with loggTilSikkerlogg true logs to sikkerlogg`() = testApplication {
        routing {
            get("/test/{value}") {
                call.withValidParam(
                    paramName = "value",
                    parse = { it.toInt() },
                    errorMessage = "Invalid number",
                    errorCode = "invalid_number",
                    logger = null,
                    loggTilSikkerlogg = true,
                ) { value ->
                    call.respondJsonString(json = """{"value":$value}""")
                }
            }
        }

        val response = client.get("/test/invalid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
    }

    @Test
    fun `withValidParam with missing parameter and loggTilSikkerlogg true logs to sikkerlogg`() = testApplication {
        routing {
            get("/test") {
                call.withValidParam(
                    paramName = "value",
                    parse = { it.toInt() },
                    errorMessage = "Missing number",
                    errorCode = "missing_number",
                    logger = null,
                    loggTilSikkerlogg = true,
                ) { value ->
                    call.respondJsonString(json = """{"value":$value}""")
                }
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Missing number","kode":"missing_number"}"""
    }

    @Test
    fun `parseBody returns Right with valid body`() = testApplication {
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

    @Test
    fun `parseBody returns Left when body is invalid`() = testApplication {
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

    @Test
    fun `parseParam returns Right with valid parameter`() = testApplication {
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

    @Test
    fun `parseParam returns Left when parameter is missing`() = testApplication {
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

    @Test
    fun `parseParam returns Left when parsing fails`() = testApplication {
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

    @Test
    fun `parseSakId returns Right with valid sakId`() = testApplication {
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

    @Test
    fun `parseSakId returns Left with invalid sakId`() = testApplication {
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

    @Test
    fun `parseSøknadId returns Right with valid søknadId`() = testApplication {
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

    @Test
    fun `parseBehandlingId returns Right with valid behandlingId`() = testApplication {
        val behandlingId = BehandlingId.random()
        routing {
            get("/test/{behandlingId}") {
                call.parseBehandlingId().fold(
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

    @Test
    fun `parseMeldekortId returns Right with valid meldekortId`() = testApplication {
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

    @Test
    fun `parseVedtakId returns Right with valid vedtakId`() = testApplication {
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

private data class TestResponseBody(
    val name: String,
    val value: Int,
)
