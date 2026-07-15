package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Header
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.TransportRespons
import no.nav.tiltakspenger.libs.httpklient.requestHeader
import no.nav.tiltakspenger.libs.httpklient.responseHeader
import no.nav.tiltakspenger.libs.httpklient.responseHeaderValues
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientHeadersTest {
    private val uri = URI.create("http://headers.test/ressurs")
    private val okJson = """{"status":"ok","antall":1}"""

    @Test
    fun `gjentatt headernavn i headere-lista gir multi-verdi-header som sendes som separate header-linjer`() = runTest {
        // Bruker custom header (X-Variant) for å unngå at Jetty automatisk gzipper response-bodyen (slik den gjør ved Accept-Encoding: gzip), som ville forstyrret body-assertions.
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/multi")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(okJson)),
            )
            val klient = testHttpKlient()

            val response = klient.getJson<TestResponseDto>(
                uri = URI.create("${wiremock.baseUrl()}/multi"),
                headere = listOf(
                    Header("X-Variant", "alpha"),
                    Header("X-Variant", "beta"),
                    Header("X-Variant", "gamma"),
                ),
            ).getOrFail()

            response.metadata.requestHeaders["X-Variant"] shouldBe listOf("alpha", "beta", "gamma")
            // Verifiser at alle tre verdiene faktisk ble sendt som separate header-linjer på wire.
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/multi"))
                    .withHeader("X-Variant", equalTo("alpha"))
                    .withHeader("X-Variant", equalTo("beta"))
                    .withHeader("X-Variant", equalTo("gamma")),
            )
        }
    }

    @Test
    fun `gjentatt headernavn med annen casing appender på samme header med første casing bevart`() = runTest {
        // HTTP-headernavn er case-insensitive (RFC 9110 §5.1): to innslag med ulik casing skal bli én header, ikke to.
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        val headers = klient.getJson<TestResponseDto>(
            uri = uri,
            headere = listOf(Header("X-Foo", "forste"), Header("x-foo", "andre")),
        ).getOrFail().metadata.requestHeaders

        headers.keys.count { it.equals("X-Foo", ignoreCase = true) } shouldBe 1
        headers.keys shouldContain "X-Foo"
        headers.keys shouldNotContain "x-foo"
        headers["X-Foo"] shouldBe listOf("forste", "andre")
    }

    @Test
    fun `headere bevarer innsettingsrekkefølge i rawRequestString, klientens default-headere havner til slutt`() = runTest {
        // En alfabetisk sortert map ville feilaktig plassert Content-Type ("C") før X-Test ("X").
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons(statusCode = 200)
        val klient = fakeHttpKlient(transport)

        val metadata = klient.postJsonUtenSvar(
            uri = uri,
            body = SerialisertJson("""{"a":1}"""),
            headere = listOf(Header("X-Test", "1")),
        ).getOrFail().metadata

        val raw = metadata.rawRequestString
        raw.indexOf("X-Test:") shouldBeLessThan raw.indexOf("Content-Type:")
    }

    @Test
    fun `reserverte headernavn avvises fail-fast uansett casing`() {
        listOf("Content-Type", "content-type", "Accept", "ACCEPT", "Authorization", "authorization", "Content-Length", "Host").forEach { navn ->
            shouldThrowWithMessage<IllegalArgumentException>("Headeren '$navn' eies av HttpKlient og settes automatisk av metoden du kaller.") {
                Header(navn, "verdi")
            }
        }
    }

    @Test
    fun `blankt headernavn avvises`() {
        shouldThrowWithMessage<IllegalArgumentException>("Headernavn kan ikke være blankt") {
            Header("  ", "verdi")
        }
    }

    @Test
    fun `sensitiv Header maskeres i rawRequestString men ikke i den strukturerte header-mappen og ikke på wire`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        val metadata = klient.getJson<TestResponseDto>(
            uri = uri,
            headere = listOf(Header("ident", "12345678901", sensitiv = true), Header("X-Vanlig", "synlig")),
        ).getOrFail().metadata

        // rawRequestString er beregnet for logging og maskerer derfor sensitive verdier.
        metadata.rawRequestString shouldContain "ident: ***"
        metadata.rawRequestString shouldContain "X-Vanlig: synlig"
        metadata.rawRequestString shouldNotContain "12345678901"
        // Den strukturerte mappen beholder ekte verdier slik at kallere kan inspisere dem programmatisk.
        metadata.requestHeaders["ident"] shouldBe listOf("12345678901")
        // Og transporten (wire) får alltid den ekte verdien.
        transport.mottatteKall.single().request.headers().firstValue("ident").orElse(null) shouldBe "12345678901"
    }

    @Test
    fun `cookie-headere maskeres i rawRequestString via standardsettet`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        val metadata = klient.getJson<TestResponseDto>(
            uri = uri,
            headere = listOf(Header("Cookie", "session=hemmelig-cookie")),
        ).getOrFail().metadata

        metadata.rawRequestString shouldContain "Cookie: ***"
        metadata.rawRequestString shouldNotContain "hemmelig-cookie"
        metadata.requestHeaders["Cookie"] shouldBe listOf("session=hemmelig-cookie")
    }

    @Test
    fun `responseHeader-aksessorene slår opp case-insensitivt`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKø(
            TransportRespons(
                statusCode = 200,
                headere = mapOf(
                    "Content-Type" to listOf("application/json"),
                    "X-Enkelt" to listOf("verdi"),
                    "X-Multi" to listOf("a", "b"),
                ),
                body = okJson.toByteArray(),
            ),
        )
        val klient = fakeHttpKlient(transport)

        val metadata = klient.getJson<TestResponseDto>(uri).getOrFail().metadata

        metadata.responseHeader("x-enkelt") shouldBe "verdi"
        metadata.responseHeader("X-ENKELT") shouldBe "verdi"
        metadata.responseHeaderValues("x-multi") shouldBe listOf("a", "b")
        metadata.responseHeader("finnes-ikke") shouldBe null
        metadata.responseHeaderValues("finnes-ikke").shouldBeEmpty()
    }

    @Test
    fun `requestHeader-aksessorene slår opp case-insensitivt`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        val metadata = klient.getJson<TestResponseDto>(
            uri = uri,
            headere = listOf(Header("X-Trace-Id", "trace-1")),
        ).getOrFail().metadata

        metadata.requestHeader("x-trace-id") shouldBe "trace-1"
    }

    @Test
    fun `NavHeadere bruker de eksakte stavemåtene nedstrømstjenestene krever`() {
        NavHeadere.xCorrelationId("id") shouldBe Header("X-Correlation-ID", "id")
        NavHeadere.navCallId("id") shouldBe Header("Nav-Call-Id", "id")
        NavHeadere.navCallid("id") shouldBe Header("Nav-Callid", "id")
        NavHeadere.navConsumerId("app") shouldBe Header("Nav-Consumer-Id", "app")
        NavHeadere.tema("IND") shouldBe Header("Tema", "IND")
        NavHeadere.behandlingsnummer("B123") shouldBe Header("behandlingsnummer", "B123")
        // Fnr i klartekst — skal alltid maskeres i rawRequestString.
        NavHeadere.ident("12345678901") shouldBe Header("ident", "12345678901", sensitiv = true)
    }
}
