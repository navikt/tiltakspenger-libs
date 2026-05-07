package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientHeadersTest {
    @Test
    fun `header overskriver tidligere verdier for samme key`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/replace"))
                    .withHeader("X-Foo", equalTo("siste"))
                    .willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/replace")) {
                header("X-Foo", "forste")
                header("X-Foo", "siste")
            }.getOrFail()

            response.body shouldBe "ok"
            response.metadata.requestHeaders["X-Foo"] shouldBe listOf("siste")
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/replace")).withHeader("X-Foo", equalTo("siste")),
            )
        }
    }

    @Test
    fun `addHeader sender flere verdier for samme key som separate header-linjer til serveren`() = runTest {
        // Bruker custom header (X-Variant) for aa unngaa at Jetty automatisk gzipper response-bodyen
        // (slik den gjoer ved Accept-Encoding: gzip), som ville forstyrret body-assertions.
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/multi")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/multi")) {
                addHeader("X-Variant", "alpha")
                addHeader("X-Variant", "beta")
                addHeader("X-Variant", "gamma")
            }.getOrFail()

            response.body shouldBe "ok"
            response.metadata.requestHeaders["X-Variant"] shouldBe listOf("alpha", "beta", "gamma")
            // Verifiser at alle tre verdiene faktisk ble sendt som separate header-linjer paa wire.
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/multi"))
                    .withHeader("X-Variant", equalTo("alpha"))
                    .withHeader("X-Variant", equalTo("beta"))
                    .withHeader("X-Variant", equalTo("gamma")),
            )
        }
    }

    @Test
    fun `addHeader kombinerer med tidligere header-kall paa samme key`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/blandet")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/blandet")) {
                header("X-Multi", "forste")
                addHeader("X-Multi", "andre")
            }.getOrFail()

            response.metadata.requestHeaders["X-Multi"] shouldBe listOf("forste", "andre")
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/blandet"))
                    .withHeader("X-Multi", equalTo("forste"))
                    .withHeader("X-Multi", equalTo("andre")),
            )
        }
    }

    @Test
    fun `header etter addHeader fjerner alle tidligere verdier`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/reset")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/reset")) {
                addHeader("X-Foo", "a")
                addHeader("X-Foo", "b")
                header("X-Foo", "kun-denne")
            }.getOrFail()

            response.metadata.requestHeaders["X-Foo"] shouldBe listOf("kun-denne")
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/reset")).withHeader("X-Foo", equalTo("kun-denne")),
            )
        }
    }
}
