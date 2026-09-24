package no.nav.tiltakspenger.libs.common

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

internal class WiremockExTest {
    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `ipv4WireMockServer rapporterer 127_0_0_1 i baseUrl og url - ikke localhost`() {
        val server = ipv4WireMockServer()
        server.start()
        try {
            server.baseUrl() shouldStartWith "http://127.0.0.1:"
            server.baseUrl() shouldBe "http://127.0.0.1:${server.port()}"
            // url() skal alltid ha nøyaktig én skråstrek mellom host og path, uansett om path har ledende skråstrek.
            server.url("/foo") shouldBe "http://127.0.0.1:${server.port()}/foo"
            server.url("foo") shouldBe "http://127.0.0.1:${server.port()}/foo"
        } finally {
            server.stop()
        }
    }

    @Test
    fun `withWireMockServer starter serveren, serverer over IPv4, og stopper etterpaa`() {
        var ref: WireMockServer? = null

        withWireMockServer { server ->
            ref = server
            server.isRunning shouldBe true

            server.stubFor(
                get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody("pong")),
            )

            val response = httpClient.send(
                HttpRequest.newBuilder(URI.create("${server.baseUrl()}/ping")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            response.statusCode() shouldBe 200
            response.body() shouldBe "pong"
        }

        // Serveren skal være stoppet etter at blokka er ferdig.
        ref!!.isRunning shouldBe false
    }

    @Test
    fun `withWireMockServer returnerer verdien fra blokka`() {
        val resultat = withWireMockServer { server -> server.port() }

        // Porten er et positivt tall; poenget er at returverdien (generisk T) propageres ut.
        (resultat > 0) shouldBe true
    }

    @Test
    fun `withWireMockServer stopper serveren selv om blokka kaster`() {
        var ref: WireMockServer? = null

        shouldThrow<IllegalStateException> {
            withWireMockServer { server ->
                ref = server
                server.isRunning shouldBe true
                error("noe gikk galt i testen")
            }
        }

        ref!!.isRunning shouldBe false
    }

    /** Antar at den frigjorte porten ikke umiddelbart gjenbrukes av en annen prosess — stabilt i praksis, men en teoretisk liten flakerisiko. */
    @Test
    fun `stoppedServerUri peker paa 127_0_0_1 med riktig path og ingen lytter`() {
        val uri = stoppedServerUri("/dod")

        uri.scheme shouldBe "http"
        uri.host shouldBe "127.0.0.1"
        uri.path shouldBe "/dod"

        // Ingen server lytter lenger -> tilkobling skal feile.
        shouldThrow<IOException> {
            httpClient.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }
    }
}
