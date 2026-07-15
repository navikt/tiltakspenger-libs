package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.left
import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.FakeHttpTransport
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.DeserializationException
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.UkjentFeil
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

internal class FellesHttpPersonklientTest {

    private val token = AccessToken("token", Instant.now(fixedClock).plusSeconds(3600))

    @Test
    fun `create gir en fungerende klient - offentlig inngang med påkrevd clock`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = pdlResponse
            }

            val pdlClient = FellesPersonklient.create(endepunkt = wiremock.baseUrl(), clock = fixedClock)

            runTest {
                pdlClient.graphqlRequest(token, "{}").getOrNull()!!
            }
        }
    }

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

            val pdlClient = FellesHttpPersonklient(
                endepunkt = wiremock.baseUrl(),
                clock = fixedClock,
                connectTimeout = 100.milliseconds,
            )
            runTest {
                pdlClient.graphqlRequest(token, "{}").getOrNull()!!
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
            val pdlClient = FellesHttpPersonklient(
                endepunkt = wiremock.baseUrl(),
                clock = fixedClock,
                connectTimeout = 100.milliseconds,
            )
            runTest {
                pdlClient.graphqlRequest(token, "body").swap().getOrNull()!! shouldBe UkjentFeil(
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

            val pdlClient = FellesHttpPersonklient(
                endepunkt = wiremock.baseUrl(),
                clock = fixedClock,
                connectTimeout = 100.milliseconds,
            )
            runTest {
                pdlClient.graphqlRequest(token, "body") shouldBe FellesPersonklientError.ResponsManglerData.left()
            }
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

            val pdlClient = FellesHttpPersonklient(
                endepunkt = wiremock.baseUrl(),
                clock = fixedClock,
                connectTimeout = 100.milliseconds,
            )
            runTest {
                pdlClient.graphqlRequest(token, "body").swap().getOrNull()!!.also {
                    it.shouldBeTypeOf<DeserializationException>()
                    it.exception.shouldBeTypeOf<tools.jackson.core.exc.StreamReadException>()
                    it.exception.message shouldContain "Unrecognized token 'asd'"
                }
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
            val pdlClient = FellesHttpPersonklient(
                endepunkt = wiremock.baseUrl(),
                clock = fixedClock,
                connectTimeout = 100.milliseconds,
            )
            runTest {
                pdlClient.graphqlRequest(token, "body").getOrNull()!!
            }
        }
    }

    @Test
    fun `sender bearer token, tema, behandlingsnummer og graphql-payloaden verbatim`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(json = pdlResponse)
        val pdlClient = FellesHttpPersonklient(
            endepunkt = "http://pdl.test/graphql",
            clock = fixedClock,
            transport = transport,
        )

        pdlClient.graphqlRequest(token, """{"query":"hentPerson"}""").getOrNull()!!

        val kall = transport.mottatteKall.single()
        kall.uri shouldBe URI.create("http://pdl.test/graphql")
        kall.request.headers().firstValue("Authorization").get() shouldBe "Bearer token"
        kall.request.headers().firstValue("Tema").get() shouldBe "IND"
        kall.request.headers().firstValue("behandlingsnummer").get() shouldBe "B470"
        kall.request.headers().firstValue("Content-Type").get() shouldBe "application/json"
        kall.request.headers().firstValue("Accept").get() shouldBe "application/json"
        kall.bodyTekst shouldBe """{"query":"hentPerson"}"""
    }

    @Test
    fun `401 mappes til Ikke2xx og logges med egen auth-melding`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(statusCode = 401, body = "unauthorized")
        val pdlClient = FellesHttpPersonklient(
            endepunkt = "http://pdl.test/graphql",
            clock = fixedClock,
            transport = transport,
        )

        pdlClient.graphqlRequest(token, "{}").swap().getOrNull()!! shouldBe FellesPersonklientError.Ikke2xx(
            status = 401,
            body = "unauthorized",
        )
    }

    @Test
    fun `request som ikke lar seg bygge mappes til NetworkError`() = runTest {
        // Ikke-ASCII-hostnavn avvises av JDK-ens HttpRequest-validering før noe sendes (RequestIkkeSendt).
        val pdlClient = FellesHttpPersonklient(
            endepunkt = "http://pdl-søm.test/graphql",
            clock = fixedClock,
            transport = FakeHttpTransport(),
        )

        pdlClient.graphqlRequest(token, "{}").swap().getOrNull()!!
            .shouldBeTypeOf<FellesPersonklientError.NetworkError>()
    }

    @Test
    fun `PDL-error med fant-ikke-person-melding mappes til FantIkkePerson`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(json = """{"errors":[{"message":"Fant ikke person","locations":[],"extensions":{"classification":"NotFound"}}],"data":null}""")
        val pdlClient = FellesHttpPersonklient(
            endepunkt = "http://pdl.test/graphql",
            clock = fixedClock,
            transport = transport,
        )

        pdlClient.graphqlRequest(token, "{}").swap().getOrNull()!! shouldBe FellesPersonklientError.FantIkkePerson
    }

    @Test
    fun `ikke-2xx mappes til Ikke2xx med status og lesbar body`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(statusCode = 500, body = "intern serverfeil")
        val pdlClient = FellesHttpPersonklient(
            endepunkt = "http://pdl.test/graphql",
            clock = fixedClock,
            transport = transport,
        )

        pdlClient.graphqlRequest(token, "{}").swap().getOrNull()!! shouldBe FellesPersonklientError.Ikke2xx(
            status = 500,
            body = "intern serverfeil",
        )
    }

    @Test
    fun `transportfeil mappes til NetworkError`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøKast(IOException("simulert nettverksfeil"))
        val pdlClient = FellesHttpPersonklient(
            endepunkt = "http://pdl.test/graphql",
            clock = fixedClock,
            transport = transport,
        )

        pdlClient.graphqlRequest(token, "{}").swap().getOrNull()!!.also {
            it.shouldBeTypeOf<FellesPersonklientError.NetworkError>()
            it.exception.message shouldBe "simulert nettverksfeil"
        }
    }
}
