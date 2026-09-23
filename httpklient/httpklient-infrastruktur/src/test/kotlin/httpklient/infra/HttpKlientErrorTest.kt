package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.UniformDistribution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Header
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

internal class HttpKlientErrorTest {
    @Test
    fun `returnerer UventetStatus ved status som ikke godtas av statusregelen`() = runTest {
        // Ingen exception — feilen utledes av selve statuskoden i finalize().
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(500, "feil", contentType = "text/plain")
        val klient = fakeHttpKlient(transport)

        val error = klient.getJson<TestResponseDto>(
            uri = URI.create("http://feil.test/feil"),
            headere = listOf(Header("X-Trace-Id", "trace-feil")),
        ).swap().getOrNull()!!

        val ikke2xx = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        ikke2xx.statusCode shouldBe 500
        ikke2xx.body shouldBe "feil"
        ikke2xx.metadata.requestHeaders["X-Trace-Id"] shouldBe listOf("trace-feil")
        ikke2xx.metadata.statusCode shouldBe 500
        ikke2xx.metadata.rawResponseString shouldBe "feil"
        ikke2xx.metadata.rawRequestString shouldBe """GET http://feil.test/feil
            |X-Trace-Id: trace-feil
            |Accept: application/json
        """.trimMargin()
    }

    @Test
    fun `returnerer SerializationError ved request-dto som ikke kan serialiseres`() = runTest {
        // Jackson kaster ved serialize() av en selvrefererende DTO, før noen HttpRequest bygges og uten at transporten kalles.
        val transport = FakeHttpTransport()
        val klient = fakeHttpKlient(transport)

        val error = klient.postJsonUtenSvar(
            uri = URI.create("http://localhost/skal-ikke-kalles"),
            body = SelvRefererendeDto(),
            headere = listOf(Header("X-Test", "serialisering")),
        ).swap().getOrNull()!!

        val serializationError = error.shouldBeInstanceOf<HttpKlientError.SerializationError>()
        serializationError.metadata.requestHeaders["X-Test"] shouldBe listOf("serialisering")
        serializationError.metadata.requestHeaders["Accept"] shouldBe null
        serializationError.metadata.requestHeaders["Content-Type"] shouldBe listOf("application/json")
        serializationError.metadata.rawRequestString shouldBe """POST http://localhost/skal-ikke-kalles
            |X-Test: serialisering
            |Content-Type: application/json
            |
            |<json-serialisering feilet>
        """.trimMargin()
        serializationError.metadata.rawResponseString shouldBe null
        transport.mottatteKall.size shouldBe 0
    }

    @Test
    fun `returnerer DeserializationError ved ugyldig json i vellykket respons`() = runTest {
        // 200-respons godtas av statusregelen, men objectMapper.readValue kaster når body ikke kan parses til måltypen.
        val transport = FakeHttpTransport()
        transport.leggIKøJson("ikke-json")
        val klient = fakeHttpKlient(transport)

        val error = klient.getJson<TestResponseDto>(URI.create("http://feil.test/ugyldig-json")).swap().getOrNull()!!

        val deserializationError = error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
        deserializationError.body shouldBe "ikke-json"
        deserializationError.statusCode shouldBe 200
        deserializationError.metadata.rawResponseString shouldBe "ikke-json"
        deserializationError.metadata.statusCode shouldBe 200
        deserializationError.metadata.requestHeaders["Accept"] shouldBe listOf("application/json")
    }

    @Test
    fun `returnerer Timeout ved request-timeout`() = runTest {
        // Java-trigger: sendAsync fullfører med java.net.http.HttpTimeoutException når serverens delay overstiger request-timeouten.
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/treg")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withFixedDelay(500)
                        .withBody("ok"),
                ),
            )
            val klient = testHttpKlient(timeout = 50.milliseconds)

            val error = klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/treg")).swap().getOrNull()!!

            val timeout = error.shouldBeInstanceOf<HttpKlientError.Timeout>()
            timeout.metadata.rawRequestString shouldBe """GET ${wiremock.baseUrl()}/treg
                |Accept: application/json
            """.trimMargin()
            timeout.metadata.rawResponseString shouldBe null
        }
    }

    @Test
    fun `returnerer Timeout ved varierende latens fra UniformDistribution`() = runTest {
        // Verifiserer at timeout-håndtering fungerer selv når serverens delay varierer (mer realistisk simulering av faktisk nettverksstøy enn fast withFixedDelay).
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/jitter")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withRandomDelay(UniformDistribution(400, 600))
                        .withBody("ok"),
                ),
            )
            val klient = testHttpKlient(timeout = 50.milliseconds)

            val error = klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/jitter")).swap().getOrNull()!!

            error.shouldBeInstanceOf<HttpKlientError.Timeout>()
        }
    }

    @Test
    fun `returnerer InvalidRequest ved header JDK avviser`() = runTest {
        // Java-trigger: HttpRequest.Builder.header(...) kaster IllegalArgumentException for et ulovlig headernavn (mellomrom), fanget i toJavaHttpRequest().
        val klient = fakeHttpKlient(FakeHttpTransport())

        val error = klient.getJson<TestResponseDto>(
            uri = URI.create("http://localhost/ugyldig"),
            headere = listOf(Header("Ugyldig Header", "verdi")),
        ).swap().getOrNull()!!

        val invalidRequest = error.shouldBeInstanceOf<HttpKlientError.InvalidRequest>()
        invalidRequest.metadata.requestHeaders["Ugyldig Header"] shouldBe listOf("verdi")
        invalidRequest.metadata.rawRequestString shouldBe """GET http://localhost/ugyldig
            |Ugyldig Header: verdi
            |Accept: application/json
        """.trimMargin()
    }

    @Test
    fun `returnerer InvalidRequest ved ugyldig uri-scheme`() = runTest {
        // Java-trigger: JDK-klienten (HttpRequest.Builder.uri) avviser scheme som ikke er http/https med IllegalArgumentException, som mappes til InvalidRequest.
        val klient = fakeHttpKlient(FakeHttpTransport())

        val error = klient.getJson<TestResponseDto>(URI.create("ftp://localhost/ugyldig")).swap().getOrNull()!!

        error.shouldBeInstanceOf<HttpKlientError.InvalidRequest>()
    }

    @Test
    fun `tom body med DTO-type gir DeserializationError med pekepinn til EllerNull`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons(statusCode = 200)
        val klient = fakeHttpKlient(transport)

        val error = klient.getJson<TestResponseDto>(URI.create("http://feil.test/tom-body")).swap().getOrNull()!!

        val deserializationError = error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
        deserializationError.statusCode shouldBe 200
        deserializationError.body shouldBe ""
        // Feilen skal peke konsumenten mot riktig metode, siden tom body nesten alltid betyr en status uten body.
        deserializationError.throwable.message shouldContain "getJsonEllerNull"
    }
}
