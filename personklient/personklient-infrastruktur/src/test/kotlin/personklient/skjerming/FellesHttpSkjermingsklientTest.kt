package no.nav.tiltakspenger.libs.personklient.skjerming

import arrow.core.nonEmptyListOf
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.stoppedServerUri
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.personklient.pdl.FellesSkjermingError
import org.junit.jupiter.api.Test
import java.time.Instant
import com.github.tomakehurst.wiremock.client.WireMock.equalTo as headerEqualTo

/**
 * Testene går via WireMock (ekte transport): klassens offentlige konstruktør er frossen og har bevisst ingen transport-søm.
 */
internal class FellesHttpSkjermingsklientTest {

    private val token = AccessToken("skjerming-token", Instant.now(fixedClock).plusSeconds(3600))
    private val fnr = Fnr.fromString("12345678901")
    private val annetFnr = Fnr.fromString("10987654321")
    private val correlationId = CorrelationId("test-correlation-id")

    private fun klient(baseUrl: String, getToken: suspend () -> AccessToken = { token }) = FellesHttpSkjermingsklient(
        endepunkt = baseUrl,
        getToken = getToken,
        clock = fixedClock,
    )

    @Test
    fun `create gir en fungerende klient - offentlig inngang med påkrevd clock`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/skjermet"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = "false"
            }

            val skjermingsklient = FellesSkjermingsklient.create(endepunkt = wiremock.baseUrl(), getToken = { token }, clock = fixedClock)

            runTest {
                skjermingsklient.erSkjermetPerson(fnr, correlationId) shouldBe false.right()
            }
        }
    }

    @Test
    fun `erSkjermetPerson parser boolsk svar og sender payload, Nav-Call-Id og bearer-token`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/skjermet"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = "true"
            }

            runTest {
                klient(wiremock.baseUrl()).erSkjermetPerson(fnr, correlationId) shouldBe true.right()
            }

            wiremock.verify(
                postRequestedFor(urlEqualTo("/skjermet"))
                    .withHeader("Nav-Call-Id", headerEqualTo("test-correlation-id"))
                    .withHeader("Authorization", headerEqualTo("Bearer skjerming-token"))
                    .withHeader("Content-Type", headerEqualTo("application/json"))
                    .withRequestBody(equalToJson("""{"personident":"12345678901"}""")),
            )
        }
    }

    @Test
    fun `erSkjermetPerson mapper false-svar`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/skjermet"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = "false"
            }

            runTest {
                klient(wiremock.baseUrl()).erSkjermetPerson(fnr, correlationId) shouldBe false.right()
            }
        }
    }

    @Test
    fun `svar som ikke er boolsk mappes til DeserializationException med body og status`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/skjermet"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = "definitivt-ikke-json"
            }

            runTest {
                klient(wiremock.baseUrl()).erSkjermetPerson(fnr, correlationId).swap().getOrNull()!!.also {
                    it.shouldBeTypeOf<FellesSkjermingError.DeserializationException>()
                    it.body shouldBe "definitivt-ikke-json"
                    it.status shouldBe 200
                }
            }
        }
    }

    @Test
    fun `ikke-2xx mappes til Ikke2xx med status og body`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/skjermet"
            } returns {
                statusCode = 503
                header = "Content-Type" to "application/json"
                body = "utilgjengelig"
            }

            runTest {
                klient(wiremock.baseUrl()).erSkjermetPerson(fnr, correlationId).swap().getOrNull()!! shouldBe FellesSkjermingError.Ikke2xx(
                    status = 503,
                    body = "utilgjengelig",
                )
            }
        }
    }

    @Test
    fun `erSkjermetPersoner mapper bulk-svar til Fnr-nøkler og dedupliserer payloaden`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/skjermetBulk"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = """{"12345678901":true,"10987654321":false}"""
            }

            runTest {
                klient(wiremock.baseUrl()).erSkjermetPersoner(nonEmptyListOf(fnr, annetFnr, fnr), correlationId) shouldBe
                    mapOf(fnr to true, annetFnr to false).right()
            }

            wiremock.verify(
                postRequestedFor(urlEqualTo("/skjermetBulk"))
                    .withRequestBody(equalToJson("""{"personidenter":["12345678901","10987654321"]}""")),
            )
        }
    }

    @Test
    fun `bulk-svar med ugyldig fnr-nøkkel mappes til DeserializationException`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/skjermetBulk"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = """{"ikke-et-fnr":true}"""
            }

            runTest {
                klient(wiremock.baseUrl()).erSkjermetPersoner(nonEmptyListOf(fnr), correlationId).swap().getOrNull()!!.also {
                    it.shouldBeTypeOf<FellesSkjermingError.DeserializationException>()
                    it.status shouldBe 200
                }
            }
        }
    }

    @Test
    fun `bulk-oppslag med ikke-2xx mappes til Ikke2xx`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/skjermetBulk"
            } returns {
                statusCode = 503
                header = "Content-Type" to "text/plain"
                body = "utilgjengelig"
            }

            runTest {
                klient(wiremock.baseUrl()).erSkjermetPersoner(nonEmptyListOf(fnr), correlationId).swap().getOrNull()!!.also {
                    it.shouldBeTypeOf<FellesSkjermingError.Ikke2xx>()
                    it.status shouldBe 503
                }
            }
        }
    }

    @Test
    fun `bulk-oppslag der getToken kaster mappes til NetworkError`() {
        withWireMockServer { wiremock ->
            runTest {
                val feil = klient(wiremock.baseUrl(), getToken = { error("token-feil") })
                    .erSkjermetPersoner(nonEmptyListOf(fnr), correlationId)
                    .swap()
                    .getOrNull()!!

                feil.shouldBeTypeOf<FellesSkjermingError.NetworkError>()
                feil.exception.message shouldBe "token-feil"
            }
        }
    }

    @Test
    fun `nettverksfeil mappes til NetworkError`() {
        // Stoppet server gir connection refused (IngenRespons) — grenen der et HTTP-forsøk ble startet uten fullstendig respons.
        val uri = stoppedServerUri("/")

        runTest {
            klient(uri.toString().removeSuffix("/"))
                .erSkjermetPerson(fnr, correlationId)
                .swap()
                .getOrNull()!!
                .shouldBeTypeOf<FellesSkjermingError.NetworkError>()
        }
    }

    @Test
    fun `getToken som kaster mappes til NetworkError, som før migreringen`() {
        withWireMockServer { wiremock ->
            runTest {
                val feil = klient(wiremock.baseUrl(), getToken = { error("token-feil") })
                    .erSkjermetPerson(fnr, correlationId)
                    .swap()
                    .getOrNull()!!

                feil.shouldBeTypeOf<FellesSkjermingError.NetworkError>()
                feil.exception.message shouldBe "token-feil"
            }
        }
    }
}
