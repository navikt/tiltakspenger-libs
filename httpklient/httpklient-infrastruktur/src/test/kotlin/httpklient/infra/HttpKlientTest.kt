package no.nav.tiltakspenger.libs.httpklient.infra

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Header
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.reflect.typeOf

/**
 * Enhetstester for request-byggingen ([byggHttpKlientRequest]): `Content-Type`/`Accept`-matrisen er en konsekvens av body og responsformat, aldri noe konsumenten setter selv.
 */
internal class HttpKlientTest {
    private val uri = URI.create("http://localhost/test")

    private fun bygg(
        body: HttpKlientRequest.Body = HttpKlientRequest.Body.Ingen,
        responsFormat: ResponsFormat = ResponsFormat.Json(typeOf<TestResponseDto>()),
        headere: List<Header> = emptyList(),
        metode: HttpMethod = HttpMethod.GET,
    ): HttpKlientRequest = byggHttpKlientRequest(
        metode = metode,
        uri = uri,
        headere = headere,
        bearerToken = null,
        godta = Statusregel.Alle2xx,
        body = body,
        responsFormat = responsFormat,
    )

    @Test
    fun `Content-Type utledes av body-typen`() {
        bygg(body = HttpKlientRequest.Body.Ingen).headers["Content-Type"] shouldBe null
        bygg(body = HttpKlientRequest.Body.Json(TestRequestDto("a", 1))).headers["Content-Type"] shouldBe listOf("application/json")
        bygg(body = HttpKlientRequest.Body.FerdigJson("{}")).headers["Content-Type"] shouldBe listOf("application/json")
        bygg(body = HttpKlientRequest.Body.Tekst("hei", sensitiv = false)).headers["Content-Type"] shouldBe listOf("text/plain; charset=utf-8")
        bygg(body = HttpKlientRequest.Body.Form("a=1")).headers["Content-Type"] shouldBe listOf("application/x-www-form-urlencoded")
    }

    @Test
    fun `Accept utledes av responsformatet`() {
        bygg(responsFormat = ResponsFormat.Json(typeOf<TestResponseDto>())).headers["Accept"] shouldBe listOf("application/json")
        bygg(responsFormat = ResponsFormat.JsonEllerNull(typeOf<TestResponseDto>(), setOf(204))).headers["Accept"] shouldBe listOf("application/json")
        bygg(responsFormat = ResponsFormat.PdfBytes).headers["Accept"] shouldBe listOf("application/pdf")
        bygg(responsFormat = ResponsFormat.IngenBody).headers["Accept"] shouldBe null
    }

    @Test
    fun `konsument-headere kommer først og klientens defaults til slutt`() {
        val request = bygg(
            body = HttpKlientRequest.Body.FerdigJson("{}"),
            headere = listOf(Header("X-Test", "1")),
            metode = HttpMethod.POST,
        )

        request.headers.keys.toList() shouldBe listOf("X-Test", "Content-Type", "Accept")
    }

    @Test
    fun `sensitive headernavn samles lowercase for redaksjonen`() {
        val request = bygg(
            headere = listOf(
                Header("Ident", "12345678901", sensitiv = true),
                Header("X-Vanlig", "synlig"),
            ),
        )

        request.sensitiveHeaderNavn shouldBe setOf("ident")
    }

    @Test
    fun `enkodFormFelter url-koder og bevarer gjentatte nøkler`() {
        enkodFormFelter(listOf("scope" to "a", "scope" to "b")) shouldBe "scope=a&scope=b"
        enkodFormFelter(listOf("grant_type" to "client_credentials", "scope" to "api://app x")) shouldBe "grant_type=client_credentials&scope=api%3A%2F%2Fapp+x"
        enkodFormFelter(emptyList()) shouldBe ""
    }

    @Test
    fun `erSuksessStatus utvides med nullVedStatus for JsonEllerNull`() {
        val vanlig = bygg(responsFormat = ResponsFormat.Json(typeOf<TestResponseDto>()))
        vanlig.erSuksessStatus(200) shouldBe true
        vanlig.erSuksessStatus(204) shouldBe true
        vanlig.erSuksessStatus(404) shouldBe false

        val ellerNull = bygg(responsFormat = ResponsFormat.JsonEllerNull(typeOf<TestResponseDto>(), setOf(404)))
        ellerNull.erSuksessStatus(200) shouldBe true
        // 404 regnes som suksess (med null-body) uten at konsumenten må gjenta den i godta.
        ellerNull.erSuksessStatus(404) shouldBe true
        ellerNull.erSuksessStatus(500) shouldBe false
    }
}
