package no.nav.tiltakspenger.libs.ktor.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

internal class ApplicationCallRespondExTest {

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

    private data class TestResponseBody(
        val name: String,
        val value: Int,
    )
}
