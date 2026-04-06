package no.nav.tiltakspenger.libs.ktor.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import no.nav.tiltakspenger.libs.ktor.common.respondJsonString
import no.nav.tiltakspenger.libs.ktor.common.withOptionalQueryParam
import no.nav.tiltakspenger.libs.ktor.common.withQueryParam
import org.junit.jupiter.api.Test

internal class ApplicationCallWithQueryParamExTest {

    @Test
    fun `withQueryParam calls onSuccess with valid query parameter`() {
        testApplication {
            routing {
                get("/test") {
                    call.withQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ) { value ->
                        call.respondJsonString(json = """{"value":$value}""")
                    }
                }
            }

            val response = client.get("/test?value=42")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":42}"""
        }
    }

    @Test
    fun `withQueryParam returns 400 when query parameter is missing`() {
        testApplication {
            routing {
                get("/test") {
                    call.withQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Missing number",
                        errorCode = "missing_number",
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
    }

    @Test
    fun `withQueryParam returns 400 when parsing fails`() {
        testApplication {
            routing {
                get("/test") {
                    call.withQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ) { value ->
                        call.respondJsonString(json = """{"value":$value}""")
                    }
                }
            }

            val response = client.get("/test?value=not-a-number")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
        }
    }

    @Test
    fun `withQueryParam with null logger works correctly`() {
        testApplication {
            routing {
                get("/test") {
                    call.withQueryParam(
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

            val response = client.get("/test?value=42")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":42}"""
        }
    }

    @Test
    fun `withQueryParam with null logger handles parsing error correctly`() {
        testApplication {
            routing {
                get("/test") {
                    call.withQueryParam(
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

            val response = client.get("/test?value=invalid")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
        }
    }

    @Test
    fun `withQueryParam with loggTilSikkerlogg false does not log to sikkerlogg`() {
        testApplication {
            routing {
                get("/test") {
                    call.withQueryParam(
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

            val response = client.get("/test?value=invalid")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
        }
    }

    @Test
    fun `withOptionalQueryParam calls onSuccess with parsed value when present`() {
        testApplication {
            routing {
                get("/test") {
                    call.withOptionalQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ) { value ->
                        call.respondJsonString(json = """{"value":$value}""")
                    }
                }
            }

            val response = client.get("/test?value=42")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":42}"""
        }
    }

    @Test
    fun `withOptionalQueryParam calls onSuccess with null when query parameter is missing`() {
        testApplication {
            routing {
                get("/test") {
                    call.withOptionalQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ) { value ->
                        call.respondJsonString(json = """{"value":${value ?: "null"}}""")
                    }
                }
            }

            val response = client.get("/test")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":null}"""
        }
    }

    @Test
    fun `withOptionalQueryParam calls onSuccess with null when query parameter is blank`() {
        testApplication {
            routing {
                get("/test") {
                    call.withOptionalQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ) { value ->
                        call.respondJsonString(json = """{"value":${value ?: "null"}}""")
                    }
                }
            }

            val response = client.get("/test?value=")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":null}"""
        }
    }

    @Test
    fun `withOptionalQueryParam returns 400 when parsing fails`() {
        testApplication {
            routing {
                get("/test") {
                    call.withOptionalQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ) { value ->
                        call.respondJsonString(json = """{"value":$value}""")
                    }
                }
            }

            val response = client.get("/test?value=not-a-number")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
        }
    }

    @Test
    fun `withOptionalQueryParam with null logger works correctly`() {
        testApplication {
            routing {
                get("/test") {
                    call.withOptionalQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                        logger = null,
                    ) { value ->
                        call.respondJsonString(json = """{"value":${value ?: "null"}}""")
                    }
                }
            }

            val response = client.get("/test")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":null}"""
        }
    }

    @Test
    fun `withOptionalQueryParam with null logger handles parsing error correctly`() {
        testApplication {
            routing {
                get("/test") {
                    call.withOptionalQueryParam(
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

            val response = client.get("/test?value=invalid")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
        }
    }
}
