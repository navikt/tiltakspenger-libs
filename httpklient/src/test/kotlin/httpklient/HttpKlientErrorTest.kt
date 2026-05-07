package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.UniformDistribution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

internal class HttpKlientErrorTest {
    @Test
    fun `returnerer Ikke2xx ved http-status utenfor 2xx`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/feil")).willReturn(aResponse().withStatus(500).withBody("feil")),
            )
            val klient = testHttpKlient()

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/feil")) {
                header("X-Trace-Id", "trace-feil")
            }.swap().getOrNull()!!

            val ikke2xx = error.shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            ikke2xx.statusCode shouldBe 500
            ikke2xx.body shouldBe "feil"
            ikke2xx.metadata.requestHeaders["X-Trace-Id"] shouldBe listOf("trace-feil")
            ikke2xx.metadata.statusCode shouldBe 500
            ikke2xx.metadata.rawResponseString shouldBe "feil"
            ikke2xx.metadata.rawRequestString shouldBe """GET ${wiremock.baseUrl()}/feil
                |X-Trace-Id: trace-feil
            """.trimMargin()
        }
    }

    @Test
    fun `returnerer SerializationError ved request-dto som ikke kan serialiseres`() = runTest {
        val klient = testHttpKlient()

        val error = klient.post<String>(URI.create("http://localhost/skal-ikke-kalles")) {
            header("X-Test", "serialisering")
            json(SelvRefererendeDto())
        }.swap().getOrNull()!!

        val serializationError = error.shouldBeInstanceOf<HttpKlientError.SerializationError>()
        serializationError.metadata.requestHeaders["X-Test"] shouldBe listOf("serialisering")
        serializationError.metadata.requestHeaders["Accept"] shouldBe null
        serializationError.metadata.requestHeaders["Content-Type"] shouldBe listOf("application/json")
        serializationError.metadata.rawRequestString shouldBe """POST http://localhost/skal-ikke-kalles
            |X-Test: serialisering
            |Content-Type: application/json
            |
            |<json serialization failed>
        """.trimMargin()
        serializationError.metadata.rawResponseString shouldBe null
    }

    @Test
    fun `returnerer DeserializationError ved ugyldig json i vellykket respons`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/ugyldig-json")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("ikke-json"),
                ),
            )
            val klient = testHttpKlient()

            val error = klient.get<TestResponseDto>(URI.create("${wiremock.baseUrl()}/ugyldig-json")).swap().getOrNull()!!

            val deserializationError = error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
            deserializationError.body shouldBe "ikke-json"
            deserializationError.statusCode shouldBe 200
            deserializationError.metadata.rawResponseString shouldBe "ikke-json"
            deserializationError.metadata.statusCode shouldBe 200
            deserializationError.metadata.requestHeaders["Accept"] shouldBe listOf("application/json")
        }
    }

    @Test
    fun `returnerer Timeout ved request-timeout`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/treg")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withFixedDelay(500)
                        .withBody("ok"),
                ),
            )
            val klient = testHttpKlient()

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/treg")) {
                timeout = 50.milliseconds
            }.swap().getOrNull()!!

            val timeout = error.shouldBeInstanceOf<HttpKlientError.Timeout>()
            timeout.metadata.rawRequestString shouldBe "GET ${wiremock.baseUrl()}/treg"
            timeout.metadata.rawResponseString shouldBe null
        }
    }

    @Test
    fun `returnerer Timeout ved varierende latens fra UniformDistribution`() = runTest {
        // Verifiserer at timeout-håndtering fungerer selv når serverens delay varierer (mer realistisk
        // simulering av faktisk nettverksstøy enn fast withFixedDelay).
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/jitter")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withRandomDelay(UniformDistribution(400, 600))
                        .withBody("ok"),
                ),
            )
            val klient = testHttpKlient()

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/jitter")) {
                timeout = 50.milliseconds
            }.swap().getOrNull()!!

            error.shouldBeInstanceOf<HttpKlientError.Timeout>()
        }
    }

    @Test
    fun `returnerer InvalidRequest ved ugyldig header`() = runTest {
        val klient = testHttpKlient()

        val error = klient.get<String>(URI.create("http://localhost/ugyldig")) {
            header("Ugyldig Header", "verdi")
        }.swap().getOrNull()!!

        val invalidRequest = error.shouldBeInstanceOf<HttpKlientError.InvalidRequest>()
        invalidRequest.metadata.requestHeaders["Ugyldig Header"] shouldBe listOf("verdi")
        invalidRequest.metadata.rawRequestString shouldBe """GET http://localhost/ugyldig
            |Ugyldig Header: verdi
        """.trimMargin()
    }

    @Test
    fun `returnerer InvalidRequest ved ugyldig uri-scheme`() = runTest {
        val klient = testHttpKlient()

        val error = klient.get<String>(URI.create("ftp://localhost/ugyldig")).swap().getOrNull()!!

        error.shouldBeInstanceOf<HttpKlientError.InvalidRequest>()
    }
}
