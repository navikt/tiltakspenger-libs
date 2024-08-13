package no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse

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
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.STRENGT_FORTROLIG
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.libs.personklient.pdl.FellesAdressebeskyttelseError
import no.nav.tiltakspenger.libs.personklient.pdl.common.withWireMockServer
import no.nav.tiltakspenger.libs.personklient.pdl.dto.AdressebeskyttelseGradering
import org.junit.jupiter.api.Test
import java.net.http.HttpConnectTimeoutException
import kotlin.time.Duration.Companion.milliseconds

internal class FellesHttpPersonTilgangsstyringKlientTest {
    private val getToken = suspend { AccessToken("token") }

    @Test
    fun `en person`() {
        withWireMockServer { wiremock ->
            val ident = Fnr.fromString("12345678901")
            wiremock.post(
                """
                {
                    "${ident.verdi}": {
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
                FellesHttpAdressebeskyttelseKlient(
                    endepunkt = wiremock.baseUrl(),
                    connectTimeout = 100.milliseconds,
                    getToken = getToken,
                )
            runTest {
                pdlClient.bolk(listOf(ident)) shouldBeRight (
                    mapOf(ident to listOf(STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND))
                    )
                pdlClient.enkel(ident) shouldBeRight listOf(
                    STRENGT_FORTROLIG,
                    STRENGT_FORTROLIG_UTLAND,
                )
            }
        }
    }

    @Test
    fun `skal hente adressebeskyttelse`() {
        withWireMockServer { wiremock ->
            val ident1 = Fnr.fromString("12345678901")
            val ident2 = Fnr.fromString("10987654321")
            wiremock.post(
                """
                {
                    "${ident1.verdi}": {"person": {"adressebeskyttelse": [ {"gradering": "STRENGT_FORTROLIG" } ]}},
                    "${ident2.verdi}": {"person": {"adressebeskyttelse": [ {"gradering": "UGRADERT" } ]}}
                }
                """.trimIndent(),
            )

            val pdlClient =
                FellesHttpAdressebeskyttelseKlient(
                    endepunkt = wiremock.baseUrl(),
                    connectTimeout = 100.milliseconds,
                    getToken = getToken,
                )
            runTest {
                pdlClient.bolk(listOf(ident1, ident2)) shouldBeRight (
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
        val ident = Fnr.fromString("12312312312")
        withWireMockServer { wiremock ->
            wiremock.post(
                """{"${ident.verdi}": null}""",
            )

            val pdlClient =
                FellesHttpAdressebeskyttelseKlient(
                    endepunkt = wiremock.baseUrl(),
                    connectTimeout = 100.milliseconds,
                    getToken = getToken,
                )

            runTest {
                pdlClient.bolk(listOf(ident)) shouldBeRight (mapOf(ident to null))
            }
        }
    }

    @Test
    fun `skal håndtere at graderingslisten er tom`() {
        val ident = Fnr.fromString("12312312312")
        withWireMockServer { wiremock ->
            wiremock.post(
                """{"${ident.verdi}": {"person": {"adressebeskyttelse": []}}}""",
            )

            val pdlClient =
                FellesHttpAdressebeskyttelseKlient(
                    endepunkt = wiremock.baseUrl(),
                    connectTimeout = 100.milliseconds,
                    getToken = getToken,
                )

            runTest {
                pdlClient.bolk(listOf(ident)) shouldBeRight (
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
                FellesHttpAdressebeskyttelseKlient(
                    endepunkt = wiremock.baseUrl(),
                    connectTimeout = 100.milliseconds,
                    getToken = getToken,
                )
            runTest {
                pdlClient.bolk(listOf(Fnr.fromString("12345678901"))).leftOrNull()!!.shouldBe(
                    FellesAdressebeskyttelseError.Ikke2xx(
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
                FellesHttpAdressebeskyttelseKlient(
                    endepunkt = wiremock.baseUrl(),
                    connectTimeout = 100.milliseconds,
                    getToken = getToken,
                )
            runTest {
                pdlClient.bolk(listOf(Fnr.fromString("12345678901"))).leftOrNull()!!.also {
                    it.shouldBeInstanceOf<FellesAdressebeskyttelseError.DeserializationException>()
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
                FellesHttpAdressebeskyttelseKlient(
                    endepunkt = wiremock.baseUrl(),
                    connectTimeout = 100.milliseconds,
                    getToken = getToken,
                )
            runBlocking {
                val job = async {
                    try {
                        val result = pdlClient.bolk(listOf(Fnr.fromString("12345678901")))
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
                FellesHttpAdressebeskyttelseKlient(
                    endepunkt = wiremock.baseUrl(),
                    connectTimeout = 1.milliseconds,
                    getToken = getToken,
                )
            runTest {
                pdlClient.bolk(listOf(Fnr.fromString("12345678901"))).leftOrNull()!!
                    .shouldBeEqualToComparingFields(
                        FellesAdressebeskyttelseError.NetworkError(
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
