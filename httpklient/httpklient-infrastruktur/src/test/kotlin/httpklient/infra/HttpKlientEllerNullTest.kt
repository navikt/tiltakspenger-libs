package no.nav.tiltakspenger.libs.httpklient.infra

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * EllerNull-variantene: statuser i `nullVedStatus` regnes som suksess med `null`-body og hopper over deserialisering.
 * Dekker meldekortservice-mønsteret (204 → null) og utbetalings simulering (204 → «ingen endring»).
 */
internal class HttpKlientEllerNullTest {
    private val uri = URI.create("http://ellernull.test/ressurs")
    private val okJson = """{"status":"ok","antall":1}"""

    @Test
    fun `getJsonEllerNull gir null-body for 204 og deserialisert body for 200`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons(statusCode = 204)
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        val tom = klient.getJsonEllerNull<TestResponseDto>(uri).getOrFail()
        tom.statusCode shouldBe 204
        tom.body shouldBe null

        val medBody = klient.getJsonEllerNull<TestResponseDto>(uri).getOrFail()
        medBody.statusCode shouldBe 200
        medBody.body shouldBe TestResponseDto(status = "ok", antall = 1)
    }

    @Test
    fun `getJsonEllerNull med default nullVedStatus gir UventetStatus for 404 - finnes-ikke-semantikk må sies eksplisitt`() = runTest {
        // En stille 404→null-default ville maskert feilstavede URI-er og feilkonfigurerte gateways som «finnes ikke».
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(404, "finnes ikke", contentType = "text/plain")
        val klient = fakeHttpKlient(transport)

        val feil = klient.getJsonEllerNull<TestResponseDto>(uri).swap().getOrNull()!!

        val uventetStatus = feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        uventetStatus.statusCode shouldBe 404
    }

    @Test
    fun `getJsonEllerNull gir null-body for 404 når 404 er med i nullVedStatus`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(404, "finnes ikke", contentType = "text/plain")
        val klient = fakeHttpKlient(transport)

        val response = klient.getJsonEllerNull<TestResponseDto>(uri, nullVedStatus = setOf(204, 404)).getOrFail()

        response.statusCode shouldBe 404
        response.body shouldBe null
        // Bodyen er fortsatt lesbar i metadata selv om dekodingen hoppes over.
        response.rawResponseString shouldBe "finnes ikke"
    }

    @Test
    fun `nullVedStatus trenger ikke gjentas i godta`() = runTest {
        // godta er fortsatt default Alle2xx; 404 regnes som suksess kun i kraft av nullVedStatus.
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(404)
        val klient = fakeHttpKlient(transport)

        klient.getJsonEllerNull<TestResponseDto>(uri, nullVedStatus = setOf(404)).getOrFail().body shouldBe null
    }

    @Test
    fun `status utenfor godta og nullVedStatus gir fortsatt UventetStatus`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(500, "feil")
        val klient = fakeHttpKlient(transport)

        val error = klient.getJsonEllerNull<TestResponseDto>(uri).swap().getOrNull()!!

        error.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 500
    }

    @Test
    fun `postJsonEllerNull gir null-body for 204 - utbetalings simulering uten endring`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons(statusCode = 204)
        val klient = fakeHttpKlient(transport)

        val response = klient.postJsonEllerNull<TestResponseDto>(uri, TestRequestDto(id = "sim", antall = 1)).getOrFail()

        response.statusCode shouldBe 204
        response.body shouldBe null
        transport.mottatteKall.single().bodyTekst shouldBe """{"id":"sim","antall":1}"""
    }

    @Test
    fun `postJsonEllerNull med SerialisertJson sender verbatim og deserialiserer 200`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        val response = klient.postJsonEllerNull<TestResponseDto>(uri, SerialisertJson("""{"ferdig":true}""")).getOrFail()

        response.body shouldBe TestResponseDto(status = "ok", antall = 1)
        transport.mottatteKall.single().bodyTekst shouldBe """{"ferdig":true}"""
    }

    @Test
    fun `ugyldig json på ikke-null-status gir DeserializationError også for EllerNull`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("ikke-json")
        val klient = fakeHttpKlient(transport)

        val error = klient.getJsonEllerNull<TestResponseDto>(uri).swap().getOrNull()!!

        error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
    }
}
