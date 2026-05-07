package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.head
import com.github.tomakehurst.wiremock.client.WireMock.options
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

internal class HttpKlientGetTest {
    @Test
    fun `request og post kan brukes uten builder`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/default-request")).willReturn(aResponse().withStatus(200).withBody("default-request")))
            wiremock.stubFor(post(urlEqualTo("/default-post")).willReturn(aResponse().withStatus(200).withBody("default-post")))
            val klient = testHttpKlient()

            klient.request<String>(URI.create("${wiremock.baseUrl()}/default-request"), HttpMethod.GET).getOrFail().body shouldBe "default-request"
            klient.post<String>(URI.create("${wiremock.baseUrl()}/default-post")).getOrFail().body shouldBe "default-post"
        }
    }

    @Test
    fun `request builder sender GET og deserialiserer response-dto`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/bruker")).withHeader("X-Trace-Id", equalTo("trace-1")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status":"ok","antall":1}"""),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.request<TestResponseDto>(URI.create("${wiremock.baseUrl()}/bruker"), HttpMethod.GET) {
                header("X-Trace-Id", "trace-1")
                timeout = 250.milliseconds
            }.getOrFail()

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
    fun `verb helpers setter riktig http method`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/get")).willReturn(aResponse().withStatus(200).withBody("get")))
            wiremock.stubFor(put(urlEqualTo("/put")).willReturn(aResponse().withStatus(200).withBody("put")))
            wiremock.stubFor(patch(urlEqualTo("/patch")).willReturn(aResponse().withStatus(200).withBody("patch")))
            wiremock.stubFor(delete(urlEqualTo("/delete")).willReturn(aResponse().withStatus(200).withBody("delete")))
            wiremock.stubFor(head(urlEqualTo("/head")).willReturn(aResponse().withStatus(204).withHeader("X-Head", "ok")))
            wiremock.stubFor(options(urlEqualTo("/options")).willReturn(aResponse().withStatus(200).withBody("options")))
            val klient = testHttpKlient()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/get")).getOrFail().body shouldBe "get"
            klient.put<String>(URI.create("${wiremock.baseUrl()}/put")).getOrFail().body shouldBe "put"
            klient.patch<String>(URI.create("${wiremock.baseUrl()}/patch")).getOrFail().body shouldBe "patch"
            klient.delete<String>(URI.create("${wiremock.baseUrl()}/delete")).getOrFail().body shouldBe "delete"
            klient.head<Unit>(URI.create("${wiremock.baseUrl()}/head")).getOrFail().metadata.responseHeaders["X-Head"] shouldBe listOf("ok")
            klient.options<String>(URI.create("${wiremock.baseUrl()}/options")).getOrFail().body shouldBe "options"
        }
    }

    @Test
    fun `kan konfigurere success status globalt`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/accepted")).willReturn(aResponse().withStatus(202).withBody("accepted")))
            val klient = testHttpKlient(successStatus = { it == 202 })

            klient.get<String>(URI.create("${wiremock.baseUrl()}/accepted")).getOrFail().body shouldBe "accepted"
        }
    }

    @Test
    fun `kan overstyre success status per request`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/not-modified")).willReturn(aResponse().withStatus(304).withBody("")))
            val klient = testHttpKlient()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/not-modified")) {
                successStatus { it == 304 }
            }.getOrFail().statusCode shouldBe 304
        }
    }

    @Test
    fun `kan deserialisere generiske response-typer som List Set og Map`() = runTest {
        withWireMock { wiremock ->
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

            val list = klient.get<List<TestResponseDto>>(URI.create("${wiremock.baseUrl()}/list")).getOrFail()
            list.body shouldBe listOf(
                TestResponseDto(status = "ok", antall = 1),
                TestResponseDto(status = "feil", antall = 2),
            )
            // Tving en faktisk feltbruk — hvis Jackson returnerte List<LinkedHashMap> ville denne kaste.
            list.body.first().status shouldBe "ok"

            val set = klient.get<Set<TestResponseDto>>(URI.create("${wiremock.baseUrl()}/set")).getOrFail()
            set.body shouldBe setOf(
                TestResponseDto(status = "ok", antall = 1),
                TestResponseDto(status = "feil", antall = 2),
            )

            val map = klient.get<Map<String, TestResponseDto>>(URI.create("${wiremock.baseUrl()}/map")).getOrFail()
            map.body shouldBe mapOf(
                "a" to TestResponseDto(status = "ok", antall = 1),
                "b" to TestResponseDto(status = "feil", antall = 2),
            )
            map.body.getValue("a").antall shouldBe 1
        }
    }
}
