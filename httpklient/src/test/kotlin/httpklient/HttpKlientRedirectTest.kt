package no.nav.tiltakspenger.libs.httpklient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient

internal class HttpKlientRedirectTest {
    @Test
    fun `default følger ikke redirects og eksponerer 3xx som UventetStatus`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/fra")).willReturn(
                    aResponse().withStatus(302).withHeader("Location", "${wiremock.baseUrl()}/til"),
                ),
            )
            wiremock.stubFor(get(urlEqualTo("/til")).willReturn(aResponse().withStatus(200).withBody("fulgt")))
            val klient = testHttpKlient()

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/fra")).swap().getOrNull()!!

            val uventet = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
            uventet.statusCode shouldBe 302
        }
    }

    @Test
    fun `followRedirects NORMAL følger redirecten`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/fra")).willReturn(
                    aResponse().withStatus(302).withHeader("Location", "${wiremock.baseUrl()}/til"),
                ),
            )
            wiremock.stubFor(get(urlEqualTo("/til")).willReturn(aResponse().withStatus(200).withBody("fulgt")))
            val klient = HttpKlient(clock = fixedClock) {
                followRedirects = HttpClient.Redirect.NORMAL
            }

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/fra")).getOrFail()

            response.statusCode shouldBe 200
            response.body shouldBe "fulgt"
        }
    }
}
