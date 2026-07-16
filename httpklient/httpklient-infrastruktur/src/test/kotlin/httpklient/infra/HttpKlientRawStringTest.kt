package no.nav.tiltakspenger.libs.httpklient.infra

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.TransportRespons
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * De lesbare tekstrepresentasjonene i metadata: trunkering ved [MAKS_RAW_STRING_LENGDE] og maskering av sensitiv tekst-body.
 * Trunkeringen stopper MB-duplisering i minne og sikkerlogg (f.eks. dokarkiv-payloads med base64-PDF).
 */
internal class HttpKlientRawStringTest {
    private val uri = URI.create("http://rawstring.test/ressurs")

    @Test
    fun `String trunkert kapper ved maksgrensen med suffiks som oppgir opprinnelig lengde`() {
        val underGrensen = "x".repeat(MAKS_RAW_STRING_LENGDE - 1)
        underGrensen.trunkert() shouldBe underGrensen

        val påGrensen = "x".repeat(MAKS_RAW_STRING_LENGDE)
        påGrensen.trunkert() shouldBe påGrensen

        val overGrensen = "x".repeat(MAKS_RAW_STRING_LENGDE + 50_000)
        val trunkert = overGrensen.trunkert()
        trunkert.length shouldBe MAKS_RAW_STRING_LENGDE + "… [trunkert, totalt 150000 tegn]".length
        trunkert shouldEndWith "… [trunkert, totalt 150000 tegn]"
    }

    @Test
    fun `stor tekstlig respons trunkeres i rawResponseString og UventetStatus-body`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(500, "y".repeat(150_000), contentType = "text/plain")
        val klient = fakeHttpKlient(transport)

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        val uventet = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        uventet.body shouldEndWith "… [trunkert, totalt 150000 tegn]"
        uventet.metadata.rawResponseString!! shouldEndWith "… [trunkert, totalt 150000 tegn]"
    }

    @Test
    fun `stor request-body trunkeres i rawRequestString men sendes komplett på wire`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons()
        val klient = fakeHttpKlient(transport)
        val storJson = """{"pdf":"${"a".repeat(150_000)}"}"""

        val response = klient.postJsonUtenSvar(uri, SerialisertJson(storJson)).getOrFail()

        response.metadata.rawRequestString shouldContain "… [trunkert, totalt "
        response.metadata.rawRequestString shouldEndWith " tegn]"
        // Kappet ved maksgrensen pluss et kort suffiks — aldri hele payloaden.
        (response.metadata.rawRequestString.length < MAKS_RAW_STRING_LENGDE + 100) shouldBe true
        // Wire-representasjonen er aldri trunkert — hele bodyen når transporten.
        transport.mottatteKall.single().bodyBytes.size shouldBe storJson.length
    }

    @Test
    fun `stor binær respons gir kort placeholder - aldri trunkert tekst`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes(ByteArray(500_000), contentType = "application/pdf")
        val klient = fakeHttpKlient(transport)

        val response = klient.getPdf(uri).getOrFail()

        response.metadata.rawResponseString shouldBe "<binær respons, 500000 bytes>"
        response.body.size shouldBe 500_000
    }

    @Test
    fun `Content-Type-header med tom verdiliste behandles som manglende og dekodes som UTF-8`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKø(TransportRespons(statusCode = 200, headere = mapOf("Content-Type" to emptyList()), body = "blåbær".toByteArray()))
        val klient = fakeHttpKlient(transport)

        klient.getPdf(uri).getOrFail().metadata.rawResponseString shouldBe "blåbær"
    }

    @Test
    fun `tom charset-parameter faller tilbake til UTF-8`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes("blåbær".toByteArray(), contentType = "text/plain; charset=")
        val klient = fakeHttpKlient(transport)

        klient.getPdf(uri).getOrFail().metadata.rawResponseString shouldBe "blåbær"
    }

    @Test
    fun `postTekst med sensitiv=true maskerer bodyen i rawRequestString men sender ekte tekst på wire`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons()
        val klient = fakeHttpKlient(transport)

        val response = klient.postTekst<Unit>(uri, tekst = "12345678901", sensitiv = true).getOrFail()

        response.metadata.rawRequestString shouldContain "***"
        response.metadata.rawRequestString shouldNotContain "12345678901"
        transport.mottatteKall.single().bodyTekst shouldBe "12345678901"
    }

    @Test
    fun `postTekst uten sensitiv viser bodyen i rawRequestString`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons()
        val klient = fakeHttpKlient(transport)

        val response = klient.postTekst<Unit>(uri, tekst = "ufarlig tekst").getOrFail()

        response.metadata.rawRequestString shouldContain "ufarlig tekst"
    }
}
