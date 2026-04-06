package no.nav.tiltakspenger.libs.ktor.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

internal class ApplicationCallParseQueryParamExTest {

    @Test
    fun `parseQueryParam returns Right with valid query parameter`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseQueryParam(
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

            val response = client.get("/test?value=42")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":42}"""
        }
    }

    @Test
    fun `parseQueryParam returns Left when query parameter is missing`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseQueryParam(
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
    fun `parseQueryParam returns Left when query parameter is blank`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseQueryParam(
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

            val response = client.get("/test?value=")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Missing number","kode":"missing_number"}"""
        }
    }

    @Test
    fun `parseQueryParam returns Left when parsing fails`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseQueryParam(
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

            val response = client.get("/test?value=not-a-number")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
        }
    }

    @Test
    fun `parseOptionalQueryParam returns Right with null when query parameter is missing`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseOptionalQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ).fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { value -> call.respondJsonString(json = """{"value":${value ?: "null"}}""") },
                    )
                }
            }

            val response = client.get("/test")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":null}"""
        }
    }

    @Test
    fun `parseOptionalQueryParam returns Right with null when query parameter is blank`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseOptionalQueryParam(
                        paramName = "value",
                        parse = { it.toInt() },
                        errorMessage = "Invalid number",
                        errorCode = "invalid_number",
                    ).fold(
                        ifLeft = { error -> call.respond400BadRequest(error) },
                        ifRight = { value -> call.respondJsonString(json = """{"value":${value ?: "null"}}""") },
                    )
                }
            }

            val response = client.get("/test?value=")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":null}"""
        }
    }

    @Test
    fun `parseOptionalQueryParam returns Right with parsed value when query parameter is present`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseOptionalQueryParam(
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

            val response = client.get("/test?value=42")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"value":42}"""
        }
    }

    @Test
    fun `parseOptionalQueryParam returns Left when parsing fails`() {
        testApplication {
            routing {
                get("/test") {
                    call.parseOptionalQueryParam(
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

            val response = client.get("/test?value=not-a-number")

            response.status shouldBe HttpStatusCode.BadRequest
            response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
            response.bodyAsText() shouldEqualJson """{"melding":"Invalid number","kode":"invalid_number"}"""
        }
    }
}
