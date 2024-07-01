package no.nav.tiltakspenger.libs.personklient.pdl

import com.github.tomakehurst.wiremock.WireMockServer
import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.tiltakspenger.libs.personklient.pdl.PDLClientError.DeserializationException
import no.nav.tiltakspenger.libs.personklient.pdl.PDLClientError.UkjentFeil
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

internal class PdlHttpClientTest {

    @Test
    fun `should be able to serialize non-errors`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = pdlResponse
            }

            val pdlClient = PdlHttpClient(endepunkt = wiremock.baseUrl(), connectTimeout = 10.milliseconds)
            pdlClient.hentPerson("ident", "token").shouldBeRight()
        }
    }

    @Test
    fun `serialisering av barn med manglende ident`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = pdlResponseManglendeIdentPåBarn
            }

            val pdlClient = PdlHttpClient(endepunkt = wiremock.baseUrl(), connectTimeout = 10.milliseconds)
            pdlClient.hentPerson("ident", "token").getOrNull()!!.also {
                val person = it.first
                val barnsIdenter = it.second
                barnsIdenter.size shouldBe 0
                person.barnUtenFolkeregisteridentifikator.size shouldBe 1
                person.barnUtenFolkeregisteridentifikator.first().fornavn shouldBe "Geometrisk"
                person.barnUtenFolkeregisteridentifikator.first().mellomnavn shouldBe "Sprudlende"
                person.barnUtenFolkeregisteridentifikator.first().etternavn shouldBe "Jakt"
                person.barnUtenFolkeregisteridentifikator.first().fødselsdato shouldBe LocalDate.of(2016, 5, 23)
                person.barnUtenFolkeregisteridentifikator.first().statsborgerskap shouldBe "BHS"
            }
        }
    }

    @Test
    fun `should be able to serialize errors`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = pdlErrorResponse
            }
            val pdlClient = PdlHttpClient(endepunkt = wiremock.baseUrl(), connectTimeout = 10.milliseconds)
            pdlClient.hentPerson("ident", "token").swap().getOrNull()!! shouldBe UkjentFeil(
                errors = listOf(
                    PdlError(
                        message = "Validation error of type FieldUndefined: Field 'rettIdentitetErUkjentadsa' in type 'FalskIdentitet' is undefined @ 'hentPerson/falskIdentitet/rettIdentitetErUkjentadsa'",
                        locations = listOf(PdlErrorLocation(line = 5, column = 7)),
                        path = null,
                        extensions = PdlErrorExtension(code = null, classification = "ValidationError"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `should handle `() {
        val response = """{ "lol": "lal" }"""
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = response
            }

            val pdlClient = PdlHttpClient(endepunkt = wiremock.baseUrl(), connectTimeout = 10.milliseconds)

            pdlClient.hentPerson("ident", "token").shouldBeLeft(PDLClientError.ResponsManglerPerson)
        }
    }

    @Test
    fun `should map invalid json to serialization`() {
        val response = """asd{ "lol": "lal" }"""
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = response
            }

            val pdlClient = PdlHttpClient(endepunkt = wiremock.baseUrl(), connectTimeout = 10.milliseconds)

            pdlClient.hentPerson("ident", "token").swap().getOrNull()!!.also {
                it.shouldBeTypeOf<DeserializationException>()
                it.exception.shouldBeTypeOf<com.fasterxml.jackson.core.JsonParseException>()
                it.exception.message shouldContain "Unrecognized token 'asd'"
            }
        }
    }

    @Test
    fun `should handle navn with null in folkeregisterdata`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = pdlResponseManglerFolkeregisterdata
            }
            val pdlClient = PdlHttpClient(endepunkt = wiremock.baseUrl(), connectTimeout = 10.milliseconds)

            pdlClient.hentPerson("ident", "token").shouldBeRight()
        }
    }
}

private fun withWireMockServer(block: (WireMockServer) -> Unit) {
    val wireMockServer = WireMockServer(0)
    wireMockServer.start()
    try {
        block(wireMockServer)
    } finally {
        wireMockServer.stop()
    }
}
