package no.nav.tiltakspenger.libs.httpklient.infra

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.FnrGenerator
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.infra.kall.MultipartDel
import no.nav.tiltakspenger.libs.httpklient.infra.kall.MultipartDeler
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Konstruksjonsguardene: feiltagelser som ikke kan gjøres til compile-feil skal kaste med pekepinn i første testkjøring, ikke gi stille runtime-divergens i produksjon.
 */
internal class HttpKlientGuardsTest {
    private val uri = URI.create("http://guards.test/ressurs")
    private val klient = fakeHttpKlient(FakeHttpTransport())

    @Test
    fun `String som Json-responstype avvises med pekepinn til rawResponseString`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>(
            "getJson<String> støttes ikke: rå respons-tekst finnes alltid i metadata.rawResponseString, og JSON skal deserialiseres til en DTO.",
        ) {
            klient.getJson<String>(uri)
        }
        shouldThrowWithMessage<IllegalArgumentException>(
            "postJson<String> støttes ikke: rå respons-tekst finnes alltid i metadata.rawResponseString, og JSON skal deserialiseres til en DTO.",
        ) {
            klient.postJson<String>(uri, TestRequestDto(id = "a", antall = 1))
        }
    }

    @Test
    fun `Unit som Json-responstype avvises med pekepinn til UtenSvar-variantene`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>("getJson<Unit> støttes ikke: bruk en UtenSvar-variant når responsen skal ignoreres.") {
            klient.getJson<Unit>(uri)
        }
    }

    @Test
    fun `ByteArray som Json-responstype avvises med pekepinn til pdf-metodene`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>("getJson<ByteArray> støttes ikke: bruk postJsonMotPdf/getPdf for binære responser.") {
            klient.getJson<ByteArray>(uri)
        }
    }

    @Test
    fun `String og ByteArray som responstype avvises også for postTekst og postForm`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>(
            "postTekst<String> støttes ikke: rå respons-tekst finnes alltid i metadata.rawResponseString, og JSON skal deserialiseres til en DTO.",
        ) {
            klient.postTekst<String>(uri, tekst = "hei")
        }
        shouldThrowWithMessage<IllegalArgumentException>("postForm<ByteArray> støttes ikke: bruk postJsonMotPdf/getPdf for binære responser.") {
            klient.postForm<ByteArray>(uri, felter = listOf("a" to "1"))
        }
    }

    @Test
    fun `Eksakt med 204 eller 205 avvises også for tekst-, form- og multipart-metodene`() = runTest {
        // Samme fail-fast som på JSON-metodene: uten den ville kallet passert alle guards og først feilet med DeserializationError ved runtime.
        shouldThrowWithMessage<IllegalArgumentException>(
            "postMultipart kan ikke godta status 204 — den har per RFC 9110 ingen body. Bruk postMultipart<Unit> når bodyen skal ignoreres.",
        ) {
            klient.postMultipart<TestResponseDto>(uri, MultipartDeler(MultipartDel("file0", "cv.pdf", "application/pdf", byteArrayOf(1))), godta = Statusregel.Eksakt(204))
        }
        shouldThrowWithMessage<IllegalArgumentException>(
            "postTekst kan ikke godta status 205 — den har per RFC 9110 ingen body. Bruk postTekst<Unit> når bodyen skal ignoreres.",
        ) {
            klient.postTekst<TestResponseDto>(uri, tekst = "hei", godta = Statusregel.Eksakt(205))
        }
        shouldThrowWithMessage<IllegalArgumentException>(
            "postForm kan ikke godta status 204 — den har per RFC 9110 ingen body. Bruk postForm<Unit> når bodyen skal ignoreres.",
        ) {
            klient.postForm<TestResponseDto>(uri, felter = listOf("a" to "1"), godta = Statusregel.Eksakt(200, 204))
        }
    }

    @Test
    fun `Eksakt med 204 er lovlig når responstypen er Unit`() = runTest {
        // Tilgangsmaskinen svarer 204 uten body, og postTekst<Unit> er den riktige måten å uttrykke det på — guarden over må ikke ta denne.
        val transport = FakeHttpTransport().apply { leggIKøTomRespons(204) }
        val respons = fakeHttpKlient(transport).postTekst<Unit>(
            uri,
            tekst = FnrGenerator().generer().verdi,
            sensitiv = true,
            godta = Statusregel.Eksakt(204),
        ).getOrFail()
        respons.statusCode shouldBe 204
    }

    @Test
    fun `String som postJson-body avvises med pekepinn til SerialisertJson og postTekst`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>(
            "postJson med String-body er tvetydig: bruk SerialisertJson for ferdig JSON, eller postTekst for rå tekst.",
        ) {
            klient.postJson<TestResponseDto>(uri, body = """{"ferdig":"json"}""")
        }
    }

    @Test
    fun `ByteArray som postJson-body avvises`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>(
            "postJson med ByteArray-body støttes ikke — meld behovet i libs hvis et endepunkt trenger rå bytes.",
        ) {
            klient.postJsonUtenSvar(uri, body = byteArrayOf(1, 2, 3))
        }
    }

    @Test
    fun `Eksakt med 204 eller 205 på ikke-nullable Json-metode avvises med pekepinn til EllerNull`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>(
            "postJson kan ikke godta status 204 — den har per RFC 9110 ingen body. Bruk postJsonEllerNull(nullVedStatus = ...) eller en UtenSvar-variant.",
        ) {
            klient.postJson<TestResponseDto>(uri, TestRequestDto(id = "a", antall = 1), godta = Statusregel.Eksakt(200, 204))
        }
        shouldThrowWithMessage<IllegalArgumentException>(
            "getJson kan ikke godta status 205 — den har per RFC 9110 ingen body. Bruk getJsonEllerNull(nullVedStatus = ...) eller en UtenSvar-variant.",
        ) {
            klient.getJson<TestResponseDto>(uri, godta = Statusregel.Eksakt(205))
        }
    }

    @Test
    fun `Eksakt med 204 eller 205 i godta på EllerNull-metode avvises med pekepinn til nullVedStatus`() = runTest {
        // 204/205 hører hjemme i nullVedStatus — i godta ville de forsøkt deserialisering av en garantert tom body ved runtime.
        shouldThrowWithMessage<IllegalArgumentException>(
            "getJsonEllerNull kan ikke godta status 204 — den har per RFC 9110 ingen body. Legg den i nullVedStatus i stedet.",
        ) {
            klient.getJsonEllerNull<TestResponseDto>(uri, nullVedStatus = setOf(404), godta = Statusregel.Eksakt(200, 204))
        }
        shouldThrowWithMessage<IllegalArgumentException>(
            "postJsonEllerNull kan ikke godta status 205 — den har per RFC 9110 ingen body. Legg den i nullVedStatus i stedet.",
        ) {
            klient.postJsonEllerNull<TestResponseDto>(uri, TestRequestDto(id = "a", antall = 1), godta = Statusregel.Eksakt(200, 205))
        }
        shouldThrowWithMessage<IllegalArgumentException>(
            "postJsonEllerNull kan ikke godta status 204 — den har per RFC 9110 ingen body. Legg den i nullVedStatus i stedet.",
        ) {
            klient.postJsonEllerNull<TestResponseDto>(uri, SerialisertJson("""{"id":"a"}"""), godta = Statusregel.Eksakt(200, 204))
        }
    }

    @Test
    fun `tom nullVedStatus avvises med pekepinn til metoden uten EllerNull`() = runTest {
        shouldThrowWithMessage<IllegalArgumentException>("nullVedStatus kan ikke være tom — bruk metoden uten EllerNull i stedet.") {
            klient.getJsonEllerNull<TestResponseDto>(uri, nullVedStatus = emptySet())
        }
    }
}
