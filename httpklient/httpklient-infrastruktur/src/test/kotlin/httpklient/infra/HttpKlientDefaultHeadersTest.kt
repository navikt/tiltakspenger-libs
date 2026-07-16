package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Header
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.requestHeader
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Karakteriserer hvem som "eier" headerne: vi setter et subset, mens `java.net.http.HttpClient` legger på sine egne transport-headere (Host, User-Agent, Content-Length) ved sending.
 * Vi fjerner dem aldri — de når serveren — men de finnes bevisst _ikke_ i [HttpKlientMetadata.requestHeaders]/`rawRequestString`.
 * Grunnen er en bevisst begrensning, ikke en forglemmelse: JDK legger dem på i det ikke-offentlige connection-laget, og `HttpRequest.headers()` eksponerer dem ikke (verifisert), så vi kan verken lese dem tilbake fra klienten eller speile dem uten å reimplementere JDK-intern oppførsel (som ville bundet oss til Java-versjonen).
 */
internal class HttpKlientDefaultHeadersTest {
    @Test
    fun `java-klienten legger på transport-headere som når serveren uten at vi fjerner dem`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/defaults")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            klient.postJsonUtenSvar(
                uri = URI.create("${wiremock.baseUrl()}/defaults"),
                body = SerialisertJson("""{"a":1}"""),
                headere = listOf(Header("X-Consumer", "satt-av-oss")),
            ).getOrFail()

            // Det serveren faktisk mottok på wire: JDK legger på transport-headere vi aldri satte, og vi fjerner dem ikke.
            val mottatt = wiremock.findAll(postRequestedFor(urlEqualTo("/defaults"))).single()
            mottatt.containsHeader("User-Agent") shouldBe true
            mottatt.getHeader("User-Agent") shouldContain "Java-http-client"
            mottatt.containsHeader("Host") shouldBe true
            mottatt.containsHeader("Content-Length") shouldBe true
            mottatt.getHeader("X-Consumer") shouldBe "satt-av-oss"
            mottatt.getHeader("Content-Type") shouldBe "application/json"
        }
    }

    @Test
    fun `metadata inneholder headerne vi setter, men ikke JDK-ens transport-headere som ikke kan leses tilbake`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/metadata")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val metadata = klient.postJsonUtenSvar(
                uri = URI.create("${wiremock.baseUrl()}/metadata"),
                body = SerialisertJson("""{"a":1}"""),
                headere = listOf(Header("X-Consumer", "satt-av-oss")),
            ).getOrFail().metadata

            // Headerne vi selv setter (konsument + klient-defaults) er med.
            metadata.requestHeader("X-Consumer") shouldBe "satt-av-oss"
            metadata.requestHeader("Content-Type") shouldBe "application/json"
            // JDK-ens transport-headere er bevisst fraværende: de eksponeres ikke av java.net.http, så vi kan ikke speile dem uten å reimplementere JDK-intern oppførsel.
            // Dokumentert på HttpKlientMetadata.requestHeaders.
            metadata.requestHeader("Host") shouldBe null
            metadata.requestHeader("User-Agent") shouldBe null
            metadata.requestHeader("Content-Length") shouldBe null
        }
    }
}
