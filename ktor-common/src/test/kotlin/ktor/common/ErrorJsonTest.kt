package no.nav.tiltakspenger.libs.ktor.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class ErrorJsonTest {

    @Test
    fun `respond403Forbidden returns 403 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond403Forbidden("Ingen tilgang", "FORBIDDEN")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Forbidden
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ingen tilgang","kode":"FORBIDDEN"}"""
    }

    @Test
    fun `respond403Forbidden with ErrorJson returns 403 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond403Forbidden(ErrorJson("Ingen tilgang", "FORBIDDEN"))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Forbidden
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ingen tilgang","kode":"FORBIDDEN"}"""
    }

    @Test
    fun `respond401Unauthorized returns 401 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond401Unauthorized("Ikke autentisert", "UNAUTHORIZED")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Unauthorized
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ikke autentisert","kode":"UNAUTHORIZED"}"""
    }

    @Test
    fun `respond401Unauthorized with ErrorJson returns 401 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond401Unauthorized(ErrorJson("Ikke autentisert", "UNAUTHORIZED"))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Unauthorized
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ikke autentisert","kode":"UNAUTHORIZED"}"""
    }

    @Test
    fun `respond500InternalServerError returns 500 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond500InternalServerError("Noe gikk galt", "INTERNAL_ERROR")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.InternalServerError
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Noe gikk galt","kode":"INTERNAL_ERROR"}"""
    }

    @Test
    fun `respond500InternalServerError with ErrorJson returns 500 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond500InternalServerError(ErrorJson("Noe gikk galt", "INTERNAL_ERROR"))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.InternalServerError
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Noe gikk galt","kode":"INTERNAL_ERROR"}"""
    }

    @Test
    fun `respond400BadRequest returns 400 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond400BadRequest("Ugyldig forespørsel", "BAD_REQUEST")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ugyldig forespørsel","kode":"BAD_REQUEST"}"""
    }

    @Test
    fun `respond400BadRequest with ErrorJson returns 400 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond400BadRequest(ErrorJson("Ugyldig forespørsel", "BAD_REQUEST"))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ugyldig forespørsel","kode":"BAD_REQUEST"}"""
    }

    @Test
    fun `respond404NotFound returns 404 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond404NotFound("Ressurs ikke funnet", "NOT_FOUND")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.NotFound
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ressurs ikke funnet","kode":"NOT_FOUND"}"""
    }

    @Test
    fun `respond404NotFound with ErrorJson returns 404 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond404NotFound(ErrorJson("Ressurs ikke funnet", "NOT_FOUND"))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.NotFound
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ressurs ikke funnet","kode":"NOT_FOUND"}"""
    }

    @Test
    fun `respond409Conflict returns 409 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond409Conflict("Konflikt med eksisterende ressurs", "CONFLICT")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Conflict
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Konflikt med eksisterende ressurs","kode":"CONFLICT"}"""
    }

    @Test
    fun `respond409Conflict with ErrorJson returns 409 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond409Conflict(ErrorJson("Konflikt med eksisterende ressurs", "CONFLICT"))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Conflict
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Konflikt med eksisterende ressurs","kode":"CONFLICT"}"""
    }

    @Test
    fun `respond501NotImplemented returns 501 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond501NotImplemented("Ikke implementert", "NOT_IMPLEMENTED")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.NotImplemented
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ikke implementert","kode":"NOT_IMPLEMENTED"}"""
    }

    @Test
    fun `respond501NotImplemented with ErrorJson returns 501 with correct body`() = testApplication {
        routing {
            get("/test") {
                call.respond501NotImplemented(ErrorJson("Ikke implementert", "NOT_IMPLEMENTED"))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.NotImplemented
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ikke implementert","kode":"NOT_IMPLEMENTED"}"""
    }

    @Test
    fun `respondError with custom status returns correct status and body`() = testApplication {
        routing {
            get("/test") {
                call.respondError(HttpStatusCode.Gone, "Ressurs er slettet", "GONE")
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.Gone
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Ressurs er slettet","kode":"GONE"}"""
    }

    @Test
    fun `respondError with ErrorJson returns correct response`() = testApplication {
        routing {
            get("/test") {
                call.respondError(HttpStatusCode.BadRequest, ErrorJson("Test", "TEST"))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Test","kode":"TEST"}"""
    }

    @Test
    fun `ErrorJson data class holds correct values`() {
        val errorJson = ErrorJson("Test melding", "TEST_KODE")

        errorJson.melding shouldBe "Test melding"
        errorJson.kode shouldBe "TEST_KODE"
    }

    @Test
    fun `ErrorJson supports copy`() {
        val original = ErrorJson("Original", "ORIGINAL")
        val copy = original.copy(melding = "Modified")

        copy.melding shouldBe "Modified"
        copy.kode shouldBe "ORIGINAL"
    }

    @Test
    fun `ErrorJson med data`() = testApplication {
        routing {
            get("/test") {
                call.respondError(HttpStatusCode.BadRequest, ErrorJsonMedData("Test", "TEST", listOf("data1", "data2")))
            }
        }

        val response = client.get("/test")

        response.status shouldBe HttpStatusCode.BadRequest
        response.headers["Content-Type"] shouldBe "application/json; charset=UTF-8"
        response.bodyAsText() shouldEqualJson """{"melding":"Test","kode":"TEST","data":["data1","data2"]}"""
    }
}
