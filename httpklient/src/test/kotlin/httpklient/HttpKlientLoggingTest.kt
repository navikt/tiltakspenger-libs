package no.nav.tiltakspenger.libs.httpklient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.stoppedServerUri
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientLoggingTest {
    @Test
    fun `kan konfigurere logging globalt og per request`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/logg-info")).willReturn(aResponse().withStatus(200).withBody("ok")))
            wiremock.stubFor(get(urlEqualTo("/logg-warn")).willReturn(aResponse().withStatus(404).withBody("borte")))
            wiremock.stubFor(get(urlEqualTo("/logg-error")).willReturn(aResponse().withStatus(500).withBody("feil")))
            val klient = testHttpKlient(
                loggingConfig = HttpKlientLoggingConfig(
                    logger = testLogger(),
                    loggTilSikkerlogg = true,
                    inkluderHeadere = true,
                ),
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/logg-info")).getOrFail().body shouldBe "ok"

            klient.get<String>(URI.create("${wiremock.baseUrl()}/logg-warn")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.UventetStatus>()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/logg-error")) {
                disableLogging()
            }.swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.UventetStatus>()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/logg-error")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        }
    }

    @Test
    fun `kan konfigurere logging med builder per request`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/per-request-logg")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val logger = testLogger()
            val klient = testHttpKlient()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/per-request-logg")) {
                logging {
                    this.logger = logger
                    loggTilSikkerlogg = true
                    inkluderHeadere = true
                }
            }.getOrFail().body shouldBe "ok"
        }
    }

    @Test
    fun `logger NetworkError til sikkerlogg når konfigurert`() = runTest {
        val uri = stoppedServerUri("/stoppet-med-logg")
        val klient = testHttpKlient(
            loggingConfig = HttpKlientLoggingConfig(
                logger = testLogger(),
                loggTilSikkerlogg = true,
            ),
        )

        klient.get<String>(uri).swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.NetworkError>()
    }

    @Test
    fun `logger på riktig nivå per status`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/ok")).willReturn(aResponse().withStatus(200).withBody("ok")))
            wiremock.stubFor(get(urlEqualTo("/klientfeil")).willReturn(aResponse().withStatus(404)))
            wiremock.stubFor(get(urlEqualTo("/serverfeil")).willReturn(aResponse().withStatus(500)))
            val logger = testLogger()
            val klient = testHttpKlient(loggingConfig = HttpKlientLoggingConfig(logger = logger))

            klient.get<String>(URI.create("${wiremock.baseUrl()}/ok"))
            klient.get<String>(URI.create("${wiremock.baseUrl()}/klientfeil"))
            klient.get<String>(URI.create("${wiremock.baseUrl()}/serverfeil"))

            verify(exactly = 1) { logger.info(any<() -> Any?>()) }
            verify(exactly = 1) { logger.warn(any<() -> Any?>()) }
            verify(exactly = 1) { logger.error(any<() -> Any?>()) }
        }
    }

    @Test
    fun `pre-flight-feil logges når logging er aktivert`() = runTest {
        // En self-refererende DTO feiler JSON-serialisering i toJavaHttpRequest (pre-flight), før noe HTTP-forsøk.
        // Slike feil skal logges på lik linje med auth-/respons-feil, ikke bli stille i drift.
        val logger = testLogger()
        val klient = testHttpKlient(loggingConfig = HttpKlientLoggingConfig(logger = logger))

        klient.post<String>(URI.create("http://localhost/pre-flight"), SelvRefererendeDto())
            .swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.SerializationError>()

        verify(exactly = 1) { logger.error(any<() -> Any?>()) }
    }

    @Test
    fun `disableLogging gir ingen logger-kall`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/stille")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val logger = testLogger()
            val klient = testHttpKlient(loggingConfig = HttpKlientLoggingConfig(logger = logger))

            klient.get<String>(URI.create("${wiremock.baseUrl()}/stille")) { disableLogging() }.getOrFail()

            verify(exactly = 0) { logger.info(any<() -> Any?>()) }
        }
    }

    @Test
    fun `sensitive headere maskeres i logg-meldingen`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/logg-redaksjon")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val logger = testLogger()
            val klient = testHttpKlient(
                loggingConfig = HttpKlientLoggingConfig(logger = logger, inkluderHeadere = true),
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/logg-redaksjon")) {
                bearerToken(testAccessToken("logg-hemmelig"))
            }.getOrFail()

            val messages = mutableListOf<() -> Any?>()
            verify { logger.info(capture(messages)) }
            val rendered = messages.joinToString("\n") { it().toString() }
            rendered.shouldNotContain("logg-hemmelig")
            rendered.shouldContain("***")
        }
    }
}
