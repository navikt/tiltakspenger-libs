package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.infra.kall.MultipartDel
import no.nav.tiltakspenger.libs.httpklient.infra.kall.MultipartDeler
import no.nav.tiltakspenger.libs.httpklient.infra.kall.tilMultipartDeler
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.requestHeader
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Verifiserer binære request-bodyer: rå bytes ([HttpKlient.postBytesMotPdf]) og `multipart/form-data` ([HttpKlient.postMultipart]).
 * Kjernegarantiene er de samme som for binære responser: bytene går uendret på wire, mens `rawRequestString` kun får en placeholder slik at filinnhold aldri kan havne i konsumentenes sikkerlogg.
 */
internal class HttpKlientBinærRequestTest {
    // PNG-magic etterfulgt av bytes som er ugyldige som UTF-8, slik at enhver charset-dekoding underveis ville korruptert innholdet.
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0xFF.toByte(), 0xFE.toByte(), 0x00)
    private val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0xFF.toByte(), 0x00)

    @Test
    fun `postBytesMotPdf sender bytene uendret med konsumentens Content-Type og mottar PDF-bytes`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/genpdf/image")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody(pdfBytes),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.postBytesMotPdf(
                uri = URI.create("${wiremock.baseUrl()}/genpdf/image"),
                bytes = pngBytes,
                contentType = "image/png",
            ).getOrFail()

            response.body.toList() shouldBe pdfBytes.toList()
            wiremock.verify(
                1,
                postRequestedFor(urlEqualTo("/genpdf/image"))
                    .withHeader("Content-Type", equalTo("image/png"))
                    .withHeader("Accept", equalTo("application/pdf")),
            )
            wiremock.findAll(postRequestedFor(urlEqualTo("/genpdf/image"))).single().body.toList() shouldBe pngBytes.toList()
        }
    }

    @Test
    fun `postBytesMotPdf gir placeholder i rawRequestString, aldri rå bytes`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes(pdfBytes, contentType = "application/pdf")

        val response = fakeHttpKlient(transport).postBytesMotPdf(
            uri = URI.create("http://localhost/genpdf/image"),
            bytes = pngBytes,
            contentType = "image/jpeg",
        ).getOrFail()

        response.metadata.rawRequestString shouldContain "<binær body, 7 bytes, image/jpeg>"
        response.metadata.requestHeader("Content-Type") shouldBe "image/jpeg"
        transport.mottatteKall.single().bodyBytes.toList() shouldBe pngBytes.toList()
    }

    @Test
    fun `postBytesMotPdf avviser blank Content-Type`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>("contentType kan ikke være blank — den blir Content-Type-headeren på requesten.") {
            testHttpKlient().postBytesMotPdf(uri = URI.create("http://localhost/genpdf"), bytes = pngBytes, contentType = " ")
        }
    }

    @Test
    fun `postBytesMotPdf avviser linjeskift i Content-Type`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>("contentType kan ikke inneholde linjeskift, var 'image/png\r\nX-Injisert: ja'") {
            testHttpKlient().postBytesMotPdf(
                uri = URI.create("http://localhost/genpdf"),
                bytes = pngBytes,
                contentType = "image/png\r\nX-Injisert: ja",
            )
        }
    }

    @Test
    fun `postMultipart bygger en RFC 7578-body med boundary som matcher Content-Type`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(listOf(TestResponseDto(status = "OK", antall = 1)))

        val response = fakeHttpKlient(transport).postMultipart<List<TestResponseDto>>(
            uri = URI.create("http://localhost/scan"),
            deler = MultipartDeler(MultipartDel(feltnavn = "file0", filnavn = "vedlegg.png", contentType = "image/png", innhold = pngBytes)),
        ).getOrFail()

        response.body shouldBe listOf(TestResponseDto(status = "OK", antall = 1))

        val kall = transport.mottatteKall.single()
        val contentType = kall.request.headers().firstValue("Content-Type").get()
        val boundary = contentType.substringAfter("boundary=")
        boundary shouldStartWith "tiltakspenger-"
        contentType shouldBe "multipart/form-data; boundary=$boundary"

        val forventet = "--$boundary\r\nContent-Disposition: form-data; name=\"file0\"; filename=\"vedlegg.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray() +
            pngBytes +
            "\r\n--$boundary--\r\n".toByteArray()
        kall.bodyBytes.toList() shouldBe forventet.toList()
    }

    @Test
    fun `postMultipart setter Accept application-json og skiller delene med boundary-en`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(emptyList<TestResponseDto>())

        val response = fakeHttpKlient(transport).postMultipart<List<TestResponseDto>>(
            uri = URI.create("http://localhost/scan"),
            deler = listOf(
                MultipartDel(feltnavn = "file0", filnavn = "en.png", contentType = "image/png", innhold = pngBytes),
                MultipartDel(feltnavn = "file1", filnavn = "to.pdf", contentType = "application/pdf", innhold = pdfBytes),
            ).tilMultipartDeler(),
        ).getOrFail()

        response.metadata.requestHeader("Accept") shouldBe "application/json"
        val kall = transport.mottatteKall.single()
        val boundary = kall.request.headers().firstValue("Content-Type").get().substringAfter("boundary=")
        val bodySomLatin1 = String(kall.bodyBytes, Charsets.ISO_8859_1)
        // Latin-1 er valgt i assertionen fordi den er byte-transparent: hver byte blir nøyaktig ett tegn, også for de binære partene.
        bodySomLatin1.split("--$boundary").size shouldBe 4
        bodySomLatin1 shouldContain "name=\"file0\"; filename=\"en.png\""
        bodySomLatin1 shouldContain "name=\"file1\"; filename=\"to.pdf\""
        bodySomLatin1 shouldContain "Content-Type: application/pdf"

        // Byte-eksakt også for flere deler: enkoderen bygger hodene separat for å kunne forhåndsberegne bodystørrelsen, og sammenføyningen må fortsatt være identisk.
        val forventet = "--$boundary\r\nContent-Disposition: form-data; name=\"file0\"; filename=\"en.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray() +
            pngBytes +
            "\r\n--$boundary\r\nContent-Disposition: form-data; name=\"file1\"; filename=\"to.pdf\"\r\nContent-Type: application/pdf\r\n\r\n".toByteArray() +
            pdfBytes +
            "\r\n--$boundary--\r\n".toByteArray()
        kall.bodyBytes.toList() shouldBe forventet.toList()
    }

    @Test
    fun `postMultipart gir sikkerlogg-trygg rawRequestString med struktur og størrelser, aldri filinnhold`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(emptyList<TestResponseDto>())

        val response = fakeHttpKlient(transport).postMultipart<List<TestResponseDto>>(
            uri = URI.create("http://localhost/scan"),
            deler = listOf(
                MultipartDel(feltnavn = "file0", filnavn = "en.png", contentType = "image/png", innhold = pngBytes),
                MultipartDel(feltnavn = "file1", filnavn = "to.pdf", contentType = "application/pdf", innhold = pdfBytes),
            ).tilMultipartDeler(),
        ).getOrFail()

        val rawRequest = response.metadata.rawRequestString
        rawRequest shouldContain "<multipart/form-data, 2 deler>"
        rawRequest shouldContain "<binær del 'file0' (en.png), 7 bytes, image/png>"
        rawRequest shouldContain "<binær del 'file1' (to.pdf), 6 bytes, application/pdf>"
        rawRequest shouldNotContain "Content-Disposition"
    }

    @Test
    fun `postMultipart escaper anførselstegn og backslash i filnavn slik at et opplastet vedlegg ikke kan bryte ut av headeren`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(emptyList<TestResponseDto>())

        fakeHttpKlient(transport).postMultipart<List<TestResponseDto>>(
            uri = URI.create("http://localhost/scan"),
            deler = MultipartDeler(MultipartDel(feltnavn = "file0", filnavn = """cv"; name="annet\.png""", contentType = "image/png", innhold = pngBytes)),
        ).getOrFail()

        String(transport.mottatteKall.single().bodyBytes, Charsets.ISO_8859_1) shouldContain
            """filename="cv\"; name=\"annet\\.png""""
    }

    @Test
    fun `postMultipart med Unit-respons ignorerer bodyen typemessig`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(200, body = "ikke json i det hele tatt", contentType = "text/plain")

        val response = fakeHttpKlient(transport).postMultipart<Unit>(
            uri = URI.create("http://localhost/scan"),
            deler = MultipartDeler(MultipartDel(feltnavn = "file0", filnavn = "en.png", contentType = "image/png", innhold = pngBytes)),
        ).getOrFail()

        response.body shouldBe Unit
        response.metadata.rawResponseString shouldBe "ikke json i det hele tatt"
    }

    @Test
    fun `postMultipart mot en ekte server sender en body serveren tolker som multipart`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/scan")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""[{"status":"OK","antall":1}]"""),
                ),
            )

            val response = testHttpKlient().postMultipart<List<TestResponseDto>>(
                uri = URI.create("${wiremock.baseUrl()}/scan"),
                deler = MultipartDeler(MultipartDel(feltnavn = "file0", filnavn = "vedlegg.png", contentType = "image/png", innhold = pngBytes)),
            ).getOrFail()

            response.body shouldBe listOf(TestResponseDto(status = "OK", antall = 1))
            // WireMock parser bodyen som multipart; at delen finnes med riktig innhold, beviser at framingen er gyldig for en ekte server.
            val mottattDel = wiremock.findAll(postRequestedFor(urlEqualTo("/scan"))).single().parts!!.single()
            mottattDel.name shouldBe "file0"
            mottattDel.body.asBytes().toList() shouldBe pngBytes.toList()
        }
    }
}
