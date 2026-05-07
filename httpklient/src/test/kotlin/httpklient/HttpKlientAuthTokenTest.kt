package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientAuthTokenTest {
    @Test
    fun `authTokenProvider gir Bearer-header paa hver request`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/a")).willReturn(aResponse().withStatus(200).withBody("ok")))
            var calls = 0
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = {
                    calls++
                    testAccessToken("tok-$calls")
                }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/a")).getOrFail()
            klient.get<String>(URI.create("${wiremock.baseUrl()}/a")).getOrFail()

            wiremock.verify(getRequestedFor(urlEqualTo("/a")).withHeader("Authorization", equalTo("Bearer tok-1")))
            wiremock.verify(getRequestedFor(urlEqualTo("/a")).withHeader("Authorization", equalTo("Bearer tok-2")))
            calls shouldBe 2
        }
    }

    @Test
    fun `per-request bearerToken overstyrer klient-default`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/b")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = { error("provider skal ikke kalles naar request setter token") }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/b")) {
                bearerToken(testAccessToken("override"))
            }.getOrFail()

            wiremock.verify(getRequestedFor(urlEqualTo("/b")).withHeader("Authorization", equalTo("Bearer override")))
        }
    }

    @Test
    fun `eksplisitt Authorization-header beholdes uendret`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/c")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = { error("provider skal ikke kalles naar Authorization er satt") }
            }

            klient.get<String>(URI.create("${wiremock.baseUrl()}/c")) {
                header("Authorization", "Basic abc")
            }.getOrFail()

            wiremock.verify(getRequestedFor(urlEqualTo("/c")).withHeader("Authorization", equalTo("Basic abc")))
        }
    }

    @Test
    fun `ingen authTokenProvider gir ingen Authorization-header`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/d")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            klient.get<String>(URI.create("${wiremock.baseUrl()}/d")).getOrFail()

            // wiremock vil reportere request uten Authorization-header — sjekk at ingen var sendt.
            wiremock.findAll(getRequestedFor(urlEqualTo("/d"))).single().header("Authorization").isPresent shouldBe false
        }
    }

    @Test
    fun `authTokenProvider som kaster gir AuthError uten HTTP-kall`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/e")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = HttpKlient(clock = fixedClock) {
                authTokenProvider = { throw IllegalStateException("token-endepunkt nede") }
            }

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/e")).swap().getOrNull()!!

            val authError = error.shouldBeInstanceOf<HttpKlientError.AuthError>()
            authError.throwable.message shouldBe "token-endepunkt nede"
            authError.retryable shouldBe false
            authError.metadata.attempts shouldBe 0
            wiremock.findAll(getRequestedFor(urlEqualTo("/e"))).size shouldBe 0
        }
    }
}
