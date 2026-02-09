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
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.DeserializationException
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.UkjentFeil
import no.nav.tiltakspenger.libs.personklient.pdl.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

internal class FellesHttpPersonklientTest {

    private val token = AccessToken("token", Instant.now().plusSeconds(3600)) {}

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
                connectTimeout = 100.milliseconds,
            )
            runTest {
                pdlClient.doGraphQLRequest(token, "{}").getOrNull()!!
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
                connectTimeout = 100.milliseconds,
            )
            runTest {
                pdlClient.graphqlRequest(token, "body").getOrNull()!!
            }
        }
    }
}
