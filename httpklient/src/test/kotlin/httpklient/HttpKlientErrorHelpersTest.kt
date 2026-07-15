package no.nav.tiltakspenger.libs.httpklient

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Hjelperne som gjør at domeneutfall kan utledes FRA feiltypen (teamkonvensjonen), uten håndbygde feiltyper på call sites.
 * Dekker tilgangsmaskin-mønsteret (403 med strukturert body) og dokarkiv/utbetaling-mønsteret (409-dedup, domene-mapping som kan feile).
 */
internal class HttpKlientErrorHelpersTest {
    private val uri = URI.create("http://helpers.test/ressurs")

    private data class AvvistDto(val begrunnelse: String, val regel: String)

    @Test
    fun `harStatus er sann kun for ResponsMottatt med en av statusene`() {
        val uventet403: HttpKlientError = HttpKlientError.UventetStatus(403, """{"begrunnelse":"nei"}""", tomMetadata(statusCode = 403))

        uventet403.harStatus(403).shouldBeTrue()
        uventet403.harStatus(403, 404).shouldBeTrue()
        uventet403.harStatus(500).shouldBeFalse()

        val nettverksfeil: HttpKlientError = HttpKlientError.NetworkError(RuntimeException(), tomMetadata())
        nettverksfeil.harStatus(403).shouldBeFalse()
    }

    @Test
    fun `bodySomJson parser strukturert feil-body til dto - tilgangsmaskin-mønsteret`() = runTest {
        // 403 er et domeneutfall og skal IKKE inn i godta; det utledes fra feiltypen.
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(403, """{"begrunnelse":"mangler rolle","regel":"ABAC-1"}""")
        val klient = fakeHttpKlient(transport)

        val error = klient.postTekst<Unit>(uri, tekst = "12345678901", sensitiv = true, godta = Statusregel.Eksakt(204)).swap().getOrNull()!!

        error.harStatus(403).shouldBeTrue()
        val avvist = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>().bodySomJson<AvvistDto>().getOrFail()
        avvist shouldBe AvvistDto(begrunnelse = "mangler rolle", regel = "ABAC-1")
    }

    @Test
    fun `bodySomJson gir DeserializationError med feilens metadata når bodyen ikke er gyldig json`() {
        val metadata = tomMetadata(rawResponseString = "<html>oops</html>", statusCode = 502)
        val uventet = HttpKlientError.UventetStatus(502, "<html>oops</html>", metadata)

        val error = uventet.bodySomJson<AvvistDto>().swap().getOrNull()!!

        error.statusCode shouldBe 502
        error.body shouldBe "<html>oops</html>"
        error.metadata shouldBe metadata
    }

    @Test
    fun `tilDomene mapper suksess-body og fanger mapping-feil som DeserializationError`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"status":"ok","antall":3}""")
        transport.leggIKøJson("""{"status":"ok","antall":-1}""")
        val klient = fakeHttpKlient(transport)

        val vellykket = klient.getJson<TestResponseDto>(uri).getOrFail().tilDomene { dto -> dto.antall * 2 }
        vellykket.getOrFail() shouldBe 6

        // Domene-mapping som kaster (f.eks. init-require i en domenetype) blir en typet feil med responsens metadata — ikke en håndbygget Either.catch på call site.
        val response = klient.getJson<TestResponseDto>(uri).getOrFail()
        val feil = response.tilDomene { dto -> require(dto.antall >= 0) { "antall kan ikke være negativt" } }.swap().getOrNull()!!

        feil.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
        feil.throwable.message shouldBe "antall kan ikke være negativt"
        feil.statusCode shouldBe 200
        feil.body shouldBe """{"status":"ok","antall":-1}"""
    }

    @Test
    fun `tilDomene på håndbygd respons uten rawResponseString feiler høyt på invariant-brudd`() {
        // Pipelinen setter alltid rawResponseString på suksess; en håndbygd respons uten skal feile tydelig, ikke gi et stille tomt body-felt i feilen.
        val håndbygd = HttpKlientResponse(statusCode = 200, body = "x", metadata = tomMetadata(rawResponseString = null))

        shouldThrowWithMessage<IllegalStateException>("Invariant brutt: rawResponseString skal alltid være satt på en suksess-respons") {
            håndbygd.tilDomene { error("mapping feilet") }
        }
    }

    @Test
    fun `authFeilUtenKall gir AuthError med samme form som klientens egne`() {
        val throwable = IllegalStateException("OBO-veksling feilet")

        val error = authFeilUtenKall(throwable)

        error.throwable shouldBe throwable
        error.retryable shouldBe false
        error.metadata.attempts shouldBe 0
        error.metadata.rawResponseString shouldBe null
    }

    @Test
    fun `loggFeil logger paret melding med throwable når feilen bærer en`() {
        val logger = testLogger()
        val error = HttpKlientError.NetworkError(RuntimeException("brutt"), tomMetadata(rawRequestString = "GET http://x"))

        error.loggFeil(logger, operasjon = "sending til datadeling", kontekst = "Sak 123")

        val meldinger = mutableListOf<() -> Any?>()
        verify(exactly = 1) { logger.error(any<Throwable>(), capture(meldinger)) }
        val melding = meldinger.single()().toString()
        melding shouldContain "sending til datadeling"
        melding shouldContain "Sak 123"
        melding shouldContain "Se sikkerlogg for detaljer"
    }

    @Test
    fun `loggFeil logger uten throwable for UventetStatus`() {
        val logger = testLogger()
        val error = HttpKlientError.UventetStatus(500, "feil", tomMetadata(statusCode = 500))

        error.loggFeil(logger, operasjon = "kall mot oppgave", kontekst = "JournalpostId 42")

        val meldinger = mutableListOf<() -> Any?>()
        verify(exactly = 1) { logger.error(capture(meldinger)) }
        val melding = meldinger.single()().toString()
        melding shouldContain "kall mot oppgave"
        melding shouldContain "Status: 500"
    }

    @Test
    fun `loggTilSikkerlogg kan kalles på suksess-responser uten å feile`() = runTest {
        // Sikkerlogg skriver til en dedikert logger; her verifiserer vi kun at hjelperen fungerer på en ekte pipeline-respons (datadeling-paritetsmønsteret).
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"status":"ok","antall":1}""")
        val klient = fakeHttpKlient(transport)

        val response = klient.getJson<TestResponseDto>(uri).getOrFail()

        response.loggTilSikkerlogg("Sendte vedtak til datadeling.")
    }
}
