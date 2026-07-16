package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
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
    fun `rawResponseString er garantert non-null på suksess fra pipelinen`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"status":"ok","antall":1}""")
        val klient = fakeHttpKlient(transport)

        val response = klient.getJson<TestResponseDto>(URI.create("http://respons.test/ok")).getOrFail()

        // Non-null aksessoren fjerner !!-mønsteret hos konsumenter som persisterer rå respons (Kabal, utbetaling).
        response.rawResponseString shouldBe """{"status":"ok","antall":1}"""
        response.rawRequestString shouldBe "GET http://respons.test/ok\nAccept: application/json"
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
    fun `Java HttpClient returnerer de tre første sifrene for positive overflow-statuser`() {
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
    fun `HttpKlient returnerer UventetStatus for WireMock status 600 med default statusregel`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/status-600")).willReturn(
                    aResponse()
                        .withStatus(600)
                        .withBody("utenfor-standard-range"),
                ),
            )
            val klient = testHttpKlient()

            val error = klient.getPdf(URI.create("${wiremock.baseUrl()}/status-600")).swap().getOrNull()!!
            val ikke2xx = error as HttpKlientError.UventetStatus

            ikke2xx.statusCode shouldBe 600
            ikke2xx.body shouldBe "utenfor-standard-range"
            ikke2xx.metadata.rawResponseString shouldBe "utenfor-standard-range"
            ikke2xx.metadata.statusCode shouldBe 600
        }
    }

    @Test
    fun `HttpKlient kan returnere status 600 hvis konsumenten eksplisitt godtar den`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/status-600")).willReturn(
                    aResponse()
                        .withStatus(600)
                        .withBody("utenfor-standard-range"),
                ),
            )
            val klient = testHttpKlient()

            klient.getPdf(URI.create("${wiremock.baseUrl()}/status-600"), godta = Statusregel.Eksakt(600)).getOrFail().statusCode shouldBe 600
        }
    }
}

private fun javaHttpResponseForRawStatusToken(statusToken: String): HttpResponse<String> {
    ServerSocket(0).use { serverSocket ->
        val serverThread = thread(start = true) {
            serverSocket.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
                while (reader.readLine()?.isNotEmpty() == true) {
                    // Leser hele requesten før vi skriver raw response.
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
