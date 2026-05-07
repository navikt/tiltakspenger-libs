package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientLoggingTest {
    @Test
    fun `kan konfigurere logging globalt og per request`() = runTest {
        withWireMock { wiremock ->
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
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/logg-error")) {
                disableLogging()
            }.swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.Ikke2xx>()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/logg-error")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
        }
    }

    @Test
    fun `kan konfigurere logging med builder per request`() = runTest {
        withWireMock { wiremock ->
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
    fun `logger NetworkError til sikkerlogg naar konfigurert`() = runTest {
        val uri = stoppedServerUri("/stoppet-med-logg")
        val klient = testHttpKlient(
            loggingConfig = HttpKlientLoggingConfig(
                logger = testLogger(),
                loggTilSikkerlogg = true,
            ),
        )

        klient.get<String>(uri).swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.NetworkError>()
    }
}
