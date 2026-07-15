package no.nav.tiltakspenger.libs.httpklient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Verifiserer binære responser (PDF via [HttpKlient.getPdf]/[HttpKlient.postJsonMotPdf]) og tekst-dekodingen i metadata.
 * Kjernegarantien er todelt: konsumenten får bytene eksakt som de kom på wire, mens metadata/feil ([HttpKlientMetadata.rawResponseString], [HttpKlientError.ResponsMottatt.body]) forblir lesbar tekst som trygt kan sendes til sikkerlogg.
 */
internal class HttpKlientByteArrayTest {
    // %PDF-magic etterfulgt av bytes som er ugyldige som UTF-8, slik at enhver charset-dekoding underveis ville korruptert innholdet.
    private val binærBody = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0xFF.toByte(), 0xFE.toByte(), 0x00, 0x9C.toByte())

    @Test
    fun `getPdf bevarer bytene eksakt og gir placeholder i rawResponseString`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/pdf")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(binærBody),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.getPdf(URI.create("${wiremock.baseUrl()}/pdf")).getOrFail()

            response.body.toList() shouldBe binærBody.toList()
            // Sikkerloggen til konsumentene skal aldri få rå binærdata — kun en placeholder.
            response.metadata.rawResponseString shouldBe "<binær respons, 8 bytes>"
        }
    }

    @Test
    fun `postJsonMotPdf sender JSON og mottar bytene eksakt`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/genpdf")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(binærBody),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.postJsonMotPdf(
                uri = URI.create("${wiremock.baseUrl()}/genpdf"),
                body = SerialisertJson("""{"brev":"vedtak"}"""),
            ).getOrFail()

            response.body.toList() shouldBe binærBody.toList()
            wiremock.verify(
                1,
                postRequestedFor(urlEqualTo("/genpdf"))
                    .withHeader("Accept", equalTo("application/pdf"))
                    .withHeader("Content-Type", equalTo("application/json")),
            )
        }
    }

    @Test
    fun `postJsonMotPdf med dto-body serialiserer payloaden og mottar bytene`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/genpdf-dto")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(binærBody),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.postJsonMotPdf(
                uri = URI.create("${wiremock.baseUrl()}/genpdf-dto"),
                body = TestRequestDto(id = "brev", antall = 1),
            ).getOrFail()

            response.body.toList() shouldBe binærBody.toList()
            wiremock.verify(
                1,
                postRequestedFor(urlEqualTo("/genpdf-dto"))
                    .withHeader("Content-Type", equalTo("application/json")),
            )
        }
    }

    @Test
    fun `Accept-headeren følger metoden - pdf for getPdf og json for getJson`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/bytes")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(binærBody)))
            wiremock.stubFor(
                get(urlEqualTo("/dto")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"status":"ok","antall":1}"""),
                ),
            )
            val klient = testHttpKlient()

            klient.getPdf(URI.create("${wiremock.baseUrl()}/bytes")).getOrFail().metadata.requestHeader("Accept") shouldBe "application/pdf"
            klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/dto")).getOrFail().metadata.requestHeader("Accept") shouldBe "application/json"

            wiremock.verify(1, getRequestedFor(urlEqualTo("/bytes")).withHeader("Accept", equalTo("application/pdf")))
            wiremock.verify(1, getRequestedFor(urlEqualTo("/dto")).withHeader("Accept", equalTo("application/json")))
        }
    }

    @Test
    fun `rawResponseString dekodes med eksplisitt charset fra Content-Type`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/latin1")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "text/plain; charset=ISO-8859-1").withBody("blåbærsyltetøy".toByteArray(Charsets.ISO_8859_1)),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.getPdf(URI.create("${wiremock.baseUrl()}/latin1")).getOrFail()

            // Tekstlig Content-Type gir dekodet tekst i metadata, uansett hvilken metode som hentet bytene.
            response.metadata.rawResponseString shouldBe "blåbærsyltetøy"
            response.body.toList() shouldBe "blåbærsyltetøy".toByteArray(Charsets.ISO_8859_1).toList()
        }
    }

    @Test
    fun `rawResponseString uten charset i Content-Type dekodes som UTF-8`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/utf8")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("blåbærsyltetøy".toByteArray(Charsets.UTF_8)),
                ),
            )
            val klient = testHttpKlient()

            klient.getPdf(URI.create("${wiremock.baseUrl()}/utf8")).getOrFail().metadata.rawResponseString shouldBe "blåbærsyltetøy"
        }
    }

    @Test
    fun `charset-parameter med mellomrom rundt likhetstegnet godtas, selv om RFC 9110 ikke tillater det`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/latin1-slurvete")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "text/plain; charset = ISO-8859-1").withBody("blåbærsyltetøy".toByteArray(Charsets.ISO_8859_1)),
                ),
            )
            val klient = testHttpKlient()

            klient.getPdf(URI.create("${wiremock.baseUrl()}/latin1-slurvete")).getOrFail().metadata.rawResponseString shouldBe "blåbærsyltetøy"
        }
    }

    @Test
    fun `respons uten Content-Type behandles som tekst med UTF-8 i metadata`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/uten-content-type")).willReturn(aResponse().withStatus(200).withBody("blåbærsyltetøy".toByteArray(Charsets.UTF_8))))
            val klient = testHttpKlient()

            klient.getPdf(URI.create("${wiremock.baseUrl()}/uten-content-type")).getOrFail().metadata.rawResponseString shouldBe "blåbærsyltetøy"
        }
    }

    @Test
    fun `ugyldig charset-navn i Content-Type faller tilbake til UTF-8 i stedet for å feile kallet`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/rart-charset")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "text/plain; charset=finnes-ikke").withBody("blåbærsyltetøy".toByteArray(Charsets.UTF_8)),
                ),
            )
            val klient = testHttpKlient()

            klient.getPdf(URI.create("${wiremock.baseUrl()}/rart-charset")).getOrFail().metadata.rawResponseString shouldBe "blåbærsyltetøy"
        }
    }

    @Test
    fun `JSON-deserialisering fra bytes fungerer, inkludert norske tegn`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/json")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"status":"blåbær","antall":2}""".toByteArray(Charsets.UTF_8)),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/json")).getOrFail()

            response.body shouldBe TestResponseDto(status = "blåbær", antall = 2)
            // JSON er tekstlig innhold, så rawResponseString forblir den lesbare JSON-en.
            response.metadata.rawResponseString shouldBe """{"status":"blåbær","antall":2}"""
        }
    }

    @Test
    fun `UventetStatus for binær respons gir placeholder i body og rawResponseString`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/binaer-feil")).willReturn(
                    aResponse().withStatus(500).withHeader("Content-Type", "application/pdf").withBody(binærBody),
                ),
            )
            val klient = testHttpKlient()

            val error = klient.getPdf(URI.create("${wiremock.baseUrl()}/binaer-feil")).swap().getOrNull()!!

            val uventetStatus = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
            uventetStatus.statusCode shouldBe 500
            uventetStatus.body shouldBe "<binær respons, 8 bytes>"
            uventetStatus.metadata.rawResponseString shouldBe "<binær respons, 8 bytes>"
        }
    }

    @Test
    fun `UventetStatus med tekstlig feil-body forblir lesbar, også for +json-suffikser`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/problem")).willReturn(
                    aResponse().withStatus(400).withHeader("Content-Type", "application/problem+json").withBody("""{"title":"ugyldig request"}"""),
                ),
            )
            val klient = testHttpKlient()

            val error = klient.getPdf(URI.create("${wiremock.baseUrl()}/problem")).swap().getOrNull()!!

            val uventetStatus = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
            uventetStatus.body shouldBe """{"title":"ugyldig request"}"""
            uventetStatus.metadata.rawResponseString shouldBe """{"title":"ugyldig request"}"""
        }
    }

    @Test
    fun `DeserializationError for binær respons gir placeholder i body, ikke rå bytes`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/binaer-som-dto")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(binærBody),
                ),
            )
            val klient = testHttpKlient()

            val error = klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/binaer-som-dto")).swap().getOrNull()!!

            val deserializationError = error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
            deserializationError.body shouldBe "<binær respons, 8 bytes>"
            deserializationError.metadata.rawResponseString shouldBe "<binær respons, 8 bytes>"
        }
    }
}
