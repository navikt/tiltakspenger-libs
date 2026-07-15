package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientRedirectTest {
    @Test
    fun `redirects følges aldri - 3xx eksponeres som UventetStatus`() = runTest {
        // followRedirects-konfig finnes ikke i v2: ingen konsument bruker redirects, så klienten følger dem aldri.
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/fra")).willReturn(
                    aResponse().withStatus(302).withHeader("Location", "${wiremock.baseUrl()}/til"),
                ),
            )
            wiremock.stubFor(get(urlEqualTo("/til")).willReturn(aResponse().withStatus(200).withBody("fulgt")))
            val klient = testHttpKlient()

            val error = klient.getPdf(URI.create("${wiremock.baseUrl()}/fra")).swap().getOrNull()!!

            val uventet = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
            uventet.statusCode shouldBe 302
            // Redirect-målet ble aldri kontaktet.
            wiremock.verify(0, getRequestedFor(urlEqualTo("/til")))
        }
    }
}
