package no.nav.tiltakspenger.libs.httpklient.infra

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.httpklient.authFeilUtenKall
import no.nav.tiltakspenger.libs.httpklient.harStatus
import no.nav.tiltakspenger.libs.httpklient.infra.feil.bodySomJson
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.httpklient.loggTilSikkerlogg
import no.nav.tiltakspenger.libs.httpklient.tryMap
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
    fun `loggTilSikkerlogg kan kalles på suksess-responser uten å feile`() = runTest {
        // Sikkerlogg skriver til en dedikert logger; her verifiserer vi kun at hjelperen fungerer på en ekte pipeline-respons (datadeling-paritetsmønsteret).
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"status":"ok","antall":1}""")
        val klient = fakeHttpKlient(transport)

        val response = klient.getJson<TestResponseDto>(uri).getOrFail()

        response.loggTilSikkerlogg("Sendte vedtak til datadeling.")
    }
}
