package no.nav.tiltakspenger.libs.personklient.pdl.pip

import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.WireMockServer
import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.fixedMs
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.STRENGT_FORTROLIG
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.libs.personklient.pdl.common.withWireMockServer
import no.nav.tiltakspenger.libs.personklient.pdl.dto.AdressebeskyttelseGradering
import org.junit.jupiter.api.Test
import java.net.http.HttpConnectTimeoutException
import kotlin.time.Duration.Companion.milliseconds

internal class FellesHttpPersonTilgangsstyringKlientTest {

    @Test
    fun `en person`() {
        withWireMockServer { wiremock ->
            val ident = "12345678901"
            wiremock.post(
                """
                {
                    "$ident": {
                        "person": {
                            "adressebeskyttelse": [
                                {"gradering": "STRENGT_FORTROLIG"},
                                {"gradering": "STRENGT_FORTROLIG_UTLAND"}
                            ]
                        }
                    }
                }
                """.trimIndent(),
            )

            val pdlClient =
                FellesHttpPersonTilgangsstyringKlient(endepunkt = wiremock.baseUrl(), connectTimeout = 100.milliseconds)
            runTest {
                pdlClient.bolk(listOf(ident), "token") shouldBeRight (
                    mapOf(ident to listOf(STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND))
                    )
                pdlClient.enkel(ident, "token") shouldBeRight listOf(STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND)
            }
        }
    }

    @Test
    fun `skal hente adressebeskyttelse`() {
        withWireMockServer { wiremock ->
            val ident1 = "12345678901"
            val ident2 = "10987654321"
            wiremock.post(
                """
                {
                    "$ident1": {"person": {"adressebeskyttelse": [ {"gradering": "STRENGT_FORTROLIG" } ]}},
                    "$ident2": {"person": {"adressebeskyttelse": [ {"gradering": "UGRADERT" } ]}}
                }
                """.trimIndent(),
            )

            val pdlClient =
                FellesHttpPersonTilgangsstyringKlient(endepunkt = wiremock.baseUrl(), connectTimeout = 100.milliseconds)
            runTest {
                pdlClient.bolk(listOf(ident1, ident2), "token") shouldBeRight (
                    mapOf(
                        ident1 to listOf(STRENGT_FORTROLIG),
                        ident2 to listOf(UGRADERT),
                    )
                    )
            }
        }
    }

    @Test
    fun `skal håndtere at identen ikke finnes`() {
        val ident = "12312312312"
        withWireMockServer { wiremock ->
            wiremock.post(
                """{"$ident": null}""",
            )

            val pdlClient =
                FellesHttpPersonTilgangsstyringKlient(endepunkt = wiremock.baseUrl(), connectTimeout = 100.milliseconds)

            runTest {
                pdlClient.bolk(listOf(ident), "token") shouldBeRight (mapOf(ident to null))
            }
        }
    }

    @Test
    fun `skal håndtere at graderingslisten er tom`() {
        val ident = "12312312312"
        withWireMockServer { wiremock ->
            wiremock.post(
                """{"$ident": {"person": {"adressebeskyttelse": []}}}""",
            )

            val pdlClient =
                FellesHttpPersonTilgangsstyringKlient(endepunkt = wiremock.baseUrl(), connectTimeout = 100.milliseconds)

            runTest {
                pdlClient.bolk(listOf(ident), "token") shouldBeRight (
                    mapOf(ident to emptyList<AdressebeskyttelseGradering>())
                    )
            }
        }
    }

    @Test
    fun `not 200`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 500
            }

            val pdlClient =
                FellesHttpPersonTilgangsstyringKlient(endepunkt = wiremock.baseUrl(), connectTimeout = 100.milliseconds)
            runTest {
                pdlClient.bolk(listOf("12345678901"), "token").leftOrNull()!!.shouldBe(
                    FellesPipError.Ikke2xx(
                        500,
                        "",
                    ),
                )
            }
        }
    }

    @Test
    fun `deserialisering feiler`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                statusCode = 200
                body = "not json"
            }

            val pdlClient =
                FellesHttpPersonTilgangsstyringKlient(endepunkt = wiremock.baseUrl(), connectTimeout = 100.milliseconds)
            runTest {
                pdlClient.bolk(listOf("12345678901"), "token").leftOrNull()!!.also {
                    it.shouldBeInstanceOf<FellesPipError.DeserializationException>()
                    it.exception.shouldBeInstanceOf<JsonParseException>()
                }
            }
        }
    }

    @Test
    fun `cancellation exception`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                delay fixedMs 200
                statusCode = 200
                header = "Content-Type" to "application/json"
                // language=JSON
                body = "\"unused\""
            }

            val pdlClient =
                FellesHttpPersonTilgangsstyringKlient(endepunkt = wiremock.baseUrl(), connectTimeout = 100.milliseconds)
            runBlocking {
                val job = async {
                    try {
                        val result = pdlClient.bolk(listOf("12345678901"), "token")
                        fail("Should have been cancelled. Result was: $result")
                    } catch (it: CancellationException) {
                        it.shouldBeInstanceOf<CancellationException>()
                    }
                }
                delay(25)
                job.cancel()
                job.join()
            }
        }
    }

    @Test
    fun `network error`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/"
            } returns {
                delay fixedMs 100
                statusCode = 200
                header = "Content-Type" to "application/json"
                // language=JSON
                body = "\"unused\""
            }

            val pdlClient =
                FellesHttpPersonTilgangsstyringKlient(endepunkt = wiremock.baseUrl(), connectTimeout = 1.milliseconds)
            runTest {
                pdlClient.bolk(listOf("12345678901"), "token").leftOrNull()!!.shouldBeEqualToComparingFields(
                    FellesPipError.NetworkError(
                        HttpConnectTimeoutException("HTTP connect timed out"),
                    ),
                )
            }
        }
    }
}

private fun WireMockServer.post(jsonBody: String) {
    this.post {
        url equalTo "/"
    } returns {
        statusCode = 200
        header = "Content-Type" to "application/json"
        // language=JSON
        body = jsonBody
    }
}
