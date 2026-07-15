package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Header
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.seconds

internal class HttpKlientGetTest {
    @Test
    fun `uri-scheme er case-insensitivt (RFC 3986) og store bokstaver godtas`() = runTest {
        // RFC 3986 §3.1: scheme er case-insensitivt. JDK-klienten lowercaser scheme selv, så HTTP:// skal fungere som http://.
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/store-scheme")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"status":"ok","antall":1}"""),
                ),
            )
            val klient = testHttpKlient()

            @Suppress("HttpUrlsUsage")
            val uppercaseScheme = wiremock.baseUrl().replaceFirst("http://", "HTTP://")
            klient.getJson<TestResponseDto>(URI.create("$uppercaseScheme/store-scheme")).getOrFail().body shouldBe TestResponseDto(status = "ok", antall = 1)
        }
    }

    @Test
    fun `getJson sender GET med konsument-headere og deserialiserer response-dto`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/bruker")).withHeader("X-Trace-Id", equalTo("trace-1")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status":"ok","antall":1}"""),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.getJson<TestResponseDto>(
                uri = URI.create("${wiremock.baseUrl()}/bruker"),
                headere = listOf(Header("X-Trace-Id", "trace-1")),
            ).getOrFail()

            response.statusCode shouldBe 200
            response.body shouldBe TestResponseDto(status = "ok", antall = 1)
            response.metadata.requestHeaders["Accept"] shouldBe listOf("application/json")
            response.metadata.requestHeaders["X-Trace-Id"] shouldBe listOf("trace-1")
            response.metadata.statusCode shouldBe 200
            response.metadata.rawResponseString shouldBe """{"status":"ok","antall":1}"""
            response.metadata.rawRequestString shouldBe """GET ${wiremock.baseUrl()}/bruker
                |X-Trace-Id: trace-1
                |Accept: application/json
            """.trimMargin()
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/bruker"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("X-Trace-Id", equalTo("trace-1")),
            )
        }
    }

    @Test
    fun `putJsonUtenSvar og patchJsonUtenSvar sender riktig http-metode`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(put(urlEqualTo("/put")).willReturn(aResponse().withStatus(204)))
            wiremock.stubFor(patch(urlEqualTo("/patch")).willReturn(aResponse().withStatus(204)))
            val klient = testHttpKlient()

            klient.putJsonUtenSvar(URI.create("${wiremock.baseUrl()}/put"), TestRequestDto(id = "1", antall = 1)).getOrFail().statusCode shouldBe 204
            klient.patchJsonUtenSvar(URI.create("${wiremock.baseUrl()}/patch"), TestRequestDto(id = "2", antall = 2)).getOrFail().statusCode shouldBe 204

            wiremock.verify(1, putRequestedFor(urlEqualTo("/put")).withHeader("Content-Type", equalTo("application/json")))
            wiremock.verify(1, patchRequestedFor(urlEqualTo("/patch")).withHeader("Content-Type", equalTo("application/json")))
        }
    }

    @Test
    fun `godta med Eksakt aksepterer en ikke-2xx-status som suksess`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/accepted")).willReturn(
                    aResponse().withStatus(202).withHeader("Content-Type", "application/json").withBody("""{"status":"accepted","antall":0}"""),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/accepted"), godta = Statusregel.Eksakt(202)).getOrFail()

            response.statusCode shouldBe 202
            response.body shouldBe TestResponseDto(status = "accepted", antall = 0)
        }
    }

    @Test
    fun `getJsonEllerNull gir null-body for status uten body (304)`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/not-modified")).willReturn(aResponse().withStatus(304).withBody("")))
            val klient = testHttpKlient()

            val response = klient.getJsonEllerNull<TestResponseDto>(
                uri = URI.create("${wiremock.baseUrl()}/not-modified"),
                nullVedStatus = setOf(304),
            ).getOrFail()

            response.statusCode shouldBe 304
            response.body shouldBe null
        }
    }

    @Test
    fun `kan deserialisere generiske response-typer som List Set og Map`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/list")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""[{"status":"ok","antall":1},{"status":"feil","antall":2}]"""),
                ),
            )
            wiremock.stubFor(
                get(urlEqualTo("/set")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""[{"status":"ok","antall":1},{"status":"feil","antall":2}]"""),
                ),
            )
            wiremock.stubFor(
                get(urlEqualTo("/map")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"a":{"status":"ok","antall":1},"b":{"status":"feil","antall":2}}"""),
                ),
            )
            val klient = testHttpKlient()

            val list = klient.getJson<List<TestResponseDto>>(URI.create("${wiremock.baseUrl()}/list")).getOrFail()
            list.body shouldBe listOf(
                TestResponseDto(status = "ok", antall = 1),
                TestResponseDto(status = "feil", antall = 2),
            )
            // Tving en faktisk feltbruk — hvis Jackson returnerte List<LinkedHashMap> ville denne kaste.
            list.body.first().status shouldBe "ok"

            val set = klient.getJson<Set<TestResponseDto>>(URI.create("${wiremock.baseUrl()}/set")).getOrFail()
            set.body shouldBe setOf(
                TestResponseDto(status = "ok", antall = 1),
                TestResponseDto(status = "feil", antall = 2),
            )

            val map = klient.getJson<Map<String, TestResponseDto>>(URI.create("${wiremock.baseUrl()}/map")).getOrFail()
            map.body shouldBe mapOf(
                "a" to TestResponseDto(status = "ok", antall = 1),
                "b" to TestResponseDto(status = "feil", antall = 2),
            )
            map.body.getValue("a").antall shouldBe 1
        }
    }

    @Test
    fun `stor respons-body leses komplett som bytes`() = runTest {
        withWireMockServer { wiremock ->
            val storBody = "x".repeat(1_000_000)
            wiremock.stubFor(get(urlEqualTo("/stor")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(storBody)))
            val klient = testHttpKlient(timeout = 5.seconds)

            val response = klient.getPdf(URI.create("${wiremock.baseUrl()}/stor")).getOrFail()

            response.body.size shouldBe 1_000_000
        }
    }
}
