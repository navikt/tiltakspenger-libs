package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

internal class HttpKlientResponseTest {
    @Test
    fun `HttpKlientResponse avviser statuskoder utenfor tresifret http-range`() {
        listOf(-1, 99, 1000).forEach { statusCode ->
            shouldThrowWithMessage<IllegalArgumentException>("statusCode must be a three-digit HTTP status code") {
                HttpKlientResponse(statusCode = statusCode, body = "body", metadata = tomMetadata())
            }
        }
    }

    @Test
    fun `HttpKlientResponse godtar tresifrede statuskoder`() {
        HttpKlientResponse(statusCode = 100, body = "body", metadata = tomMetadata()).statusCode shouldBe 100
        HttpKlientResponse(statusCode = 599, body = "body", metadata = tomMetadata()).statusCode shouldBe 599
        HttpKlientResponse(statusCode = 600, body = "body", metadata = tomMetadata()).statusCode shouldBe 600
    }

    @Test
    fun `Java HttpClient returnerer tresifrede statuser som HttpKlientResponse aksepterer`() {
        listOf("200", "599", "600", "999").forEach { statusToken ->
            val response = javaHttpResponseForRawStatusToken(statusToken)
            val statusCode = statusToken.toInt()

            response.statusCode() shouldBe statusCode
            HttpKlientResponse(
                statusCode = response.statusCode(),
                body = response.body(),
                metadata = tomMetadata(responseHeaders = response.headers().map()),
            ).statusCode shouldBe statusCode
        }
    }

    @Test
    fun `Java HttpClient returnerer de tre forste sifrene for positive overflow-statuser`() {
        mapOf(
            "2147483648" to 214,
            "9223372036854775808" to 922,
        ).forEach { (statusToken, expectedStatusCode) ->
            val response = javaHttpResponseForRawStatusToken(statusToken)

            response.statusCode() shouldBe expectedStatusCode
            HttpKlientResponse(
                statusCode = response.statusCode(),
                body = response.body(),
                metadata = tomMetadata(responseHeaders = response.headers().map()),
            ).statusCode shouldBe expectedStatusCode
        }
    }

    @Test
    fun `Java HttpClient avviser ugyldige raw statuslinjer`() {
        listOf(
            "99",
            "1000",
            "-2147483649",
            "-9223372036854775809",
        ).forEach { statusToken ->
            shouldThrow<Exception> {
                javaHttpResponseForRawStatusToken(statusToken)
            }
        }
    }

    @Test
    fun `Java HttpClient returnerer status 600 fra WireMock`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/status-600")).willReturn(
                    aResponse()
                        .withStatus(600)
                        .withBody("utenfor-standard-range"),
                ),
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${wiremock.baseUrl()}/status-600"))
                .GET()
                .build()

            val response = HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .get()

            response.statusCode() shouldBe 600
            response.body() shouldBe "utenfor-standard-range"
        }
    }

    @Test
    fun `HttpKlient returnerer Ikke2xx for WireMock status 600 med default successStatus`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/status-600")).willReturn(
                    aResponse()
                        .withStatus(600)
                        .withBody("utenfor-standard-range"),
                ),
            )
            val klient = testHttpKlient()

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/status-600")).swap().getOrNull()!!
            val ikke2xx = error as HttpKlientError.Ikke2xx

            ikke2xx.statusCode shouldBe 600
            ikke2xx.body shouldBe "utenfor-standard-range"
            ikke2xx.metadata.rawRequestString shouldBe "GET ${wiremock.baseUrl()}/status-600"
            ikke2xx.metadata.rawResponseString shouldBe "utenfor-standard-range"
            ikke2xx.metadata.statusCode shouldBe 600
            ikke2xx.metadata.responseHeaders[":status"] shouldBe listOf("600")
        }
    }

    @Test
    fun `HttpKlient kan returnere status 600 hvis konsumenten eksplisitt definerer den som success`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/status-600")).willReturn(
                    aResponse()
                        .withStatus(600)
                        .withBody("utenfor-standard-range"),
                ),
            )
            val klient = testHttpKlient(successStatus = { it == 600 })

            klient.get<String>(URI.create("${wiremock.baseUrl()}/status-600")).getOrFail().statusCode shouldBe 600
        }
    }
}

private fun javaHttpResponseForRawStatusToken(statusToken: String): HttpResponse<String> {
    ServerSocket(0).use { serverSocket ->
        val serverThread = thread(start = true) {
            serverSocket.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                while (reader.readLine()?.isNotEmpty() == true) {
                    // Leser hele requesten foer vi skriver raw response.
                }
                socket.getOutputStream().writer(StandardCharsets.US_ASCII).use { writer ->
                    writer.write("HTTP/1.1 $statusToken Test\r\n")
                    writer.write("Content-Length: 2\r\n")
                    writer.write("Connection: close\r\n")
                    writer.write("\r\n")
                    writer.write("OK")
                    writer.flush()
                }
            }
        }
        return try {
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:${serverSocket.localPort}/raw-status"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } finally {
            serverThread.join(1000)
        }
    }
}
