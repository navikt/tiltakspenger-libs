package no.nav.tiltakspenger.libs.httpklient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientHeadersTest {
    @Test
    fun `header overskriver tidligere verdier for samme key`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/replace"))
                    .withHeader("X-Foo", equalTo("siste"))
                    .willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/replace")) {
                header("X-Foo", "forste")
                header("X-Foo", "siste")
            }.getOrFail()

            response.body shouldBe "ok"
            response.metadata.requestHeaders["X-Foo"] shouldBe listOf("siste")
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/replace")).withHeader("X-Foo", equalTo("siste")),
            )
        }
    }

    @Test
    fun `addHeader sender flere verdier for samme key som separate header-linjer til serveren`() = runTest {
        // Bruker custom header (X-Variant) for å unngå at Jetty automatisk gzipper response-bodyen (slik den gjør ved Accept-Encoding: gzip), som ville forstyrret body-assertions.
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/multi")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/multi")) {
                addHeader("X-Variant", "alpha")
                addHeader("X-Variant", "beta")
                addHeader("X-Variant", "gamma")
            }.getOrFail()

            response.body shouldBe "ok"
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
    fun `addHeader kombinerer med tidligere header-kall på samme key`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/blandet")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/blandet")) {
                header("X-Multi", "forste")
                addHeader("X-Multi", "andre")
            }.getOrFail()

            response.metadata.requestHeaders["X-Multi"] shouldBe listOf("forste", "andre")
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/blandet"))
                    .withHeader("X-Multi", equalTo("forste"))
                    .withHeader("X-Multi", equalTo("andre")),
            )
        }
    }

    @Test
    fun `header etter addHeader fjerner alle tidligere verdier`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/reset")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/reset")) {
                addHeader("X-Foo", "a")
                addHeader("X-Foo", "b")
                header("X-Foo", "kun-denne")
            }.getOrFail()

            response.metadata.requestHeaders["X-Foo"] shouldBe listOf("kun-denne")
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/reset")).withHeader("X-Foo", equalTo("kun-denne")),
            )
        }
    }

    @Test
    fun `header slår opp eksisterende key case-insensitivt og overskriver i stedet for å lage duplikat`() = runTest {
        // HTTP-headernavn er case-insensitive (RFC 9110 §5.1), så header("X-Foo") etterfulgt av header("x-foo") skal overskrive samme header – ikke sende to separate header-linjer til serveren.
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/case-replace")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val metadata = klient.get<String>(URI.create("${wiremock.baseUrl()}/case-replace")) {
                header("X-Foo", "forste")
                header("x-foo", "siste")
            }.getOrFail().metadata

            // Kun én header beholdes på tvers av casing, med den sist satte verdien.
            metadata.requestHeaders.keys.count { it.equals("X-Foo", ignoreCase = true) } shouldBe 1
            metadata.requestHeaderValues("X-Foo") shouldBe listOf("siste")
            // På wire skal serveren se nøyaktig én verdi for headeren, og den gamle verdien skal være borte.
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/case-replace"))
                    .withHeader("X-Foo", equalTo("siste")),
            )
            wiremock.verify(
                0,
                getRequestedFor(urlEqualTo("/case-replace"))
                    .withHeader("X-Foo", equalTo("forste")),
            )
        }
    }

    @Test
    fun `addHeader slår opp eksisterende key case-insensitivt og appender på samme header`() = runTest {
        // addHeader("X-Variant") etterfulgt av addHeader("x-variant") skal legge verdien på samme header, ikke opprette en duplikat-header med annen casing.
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/case-append")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val metadata = klient.get<String>(URI.create("${wiremock.baseUrl()}/case-append")) {
                addHeader("X-Variant", "alpha")
                addHeader("x-variant", "beta")
            }.getOrFail().metadata

            metadata.requestHeaders.keys.count { it.equals("X-Variant", ignoreCase = true) } shouldBe 1
            metadata.requestHeaderValues("X-Variant") shouldBe listOf("alpha", "beta")
            wiremock.verify(
                1,
                getRequestedFor(urlEqualTo("/case-append"))
                    .withHeader("X-Variant", equalTo("alpha"))
                    .withHeader("X-Variant", equalTo("beta")),
            )
        }
    }

    @Test
    fun `headere bevarer innsettingsrekkefølge i rawRequestString, default-headere havner til slutt`() = runTest {
        // HttpKlientRequest-kontrakten lover at headere bevarer rekkefølgen konsumenten satte dem i, med klientens default-headere (her Content-Type for JSON-body) lagt til på slutten.
        // En alfabetisk sortert map ville feilaktig plassert Content-Type ("C") før X-Test ("X").
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/rekkefolge")).willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            val metadata = klient.post<String>(URI.create("${wiremock.baseUrl()}/rekkefolge"), """{"a":1}""") {
                header("X-Test", "1")
            }.getOrFail().metadata

            val raw = metadata.rawRequestString
            raw.indexOf("X-Test:") shouldBeLessThan raw.indexOf("Content-Type:")
        }
    }

    @Test
    fun `header etterfulgt av header med annen casing bruker den siste casingen`() = runTest {
        // Den siste skriveren bestemmer casingen: header("min-header") så header("Min-header") skal ende opp med nøkkelen "Min-header".
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/casing-hh")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val headers = klient.get<String>(URI.create("${wiremock.baseUrl()}/casing-hh")) {
                header("min-header", "forste")
                header("Min-header", "siste")
            }.getOrFail().metadata.requestHeaders

            headers.keys shouldContain "Min-header"
            headers.keys shouldNotContain "min-header"
            headers["Min-header"] shouldBe listOf("siste")
        }
    }

    @Test
    fun `header etterfulgt av addHeader med annen casing bruker den siste casingen og beholder begge verdier`() = runTest {
        // header("min-header") så addHeader("Min-header") skal appende på samme header, med nøkkelen oppdatert til den siste casingen "Min-header".
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/casing-ha")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val headers = klient.get<String>(URI.create("${wiremock.baseUrl()}/casing-ha")) {
                header("min-header", "forste")
                addHeader("Min-header", "siste")
            }.getOrFail().metadata.requestHeaders

            headers.keys shouldContain "Min-header"
            headers.keys shouldNotContain "min-header"
            headers["Min-header"] shouldBe listOf("forste", "siste")
        }
    }

    @Test
    fun `addHeader etterfulgt av header med annen casing bruker den siste casingen og overskriver verdiene`() = runTest {
        // addHeader("min-header") så header("Min-header") skal overskrive verdien og bruke den siste casingen "Min-header".
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/casing-ah")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val headers = klient.get<String>(URI.create("${wiremock.baseUrl()}/casing-ah")) {
                addHeader("min-header", "forste")
                header("Min-header", "siste")
            }.getOrFail().metadata.requestHeaders

            headers.keys shouldContain "Min-header"
            headers.keys shouldNotContain "min-header"
            headers["Min-header"] shouldBe listOf("siste")
        }
    }

    @Test
    fun `addHeader etterfulgt av addHeader med annen casing bruker den siste casingen og beholder begge verdier`() = runTest {
        // addHeader("min-header") så addHeader("Min-header") skal appende på samme header, med nøkkelen oppdatert til den siste casingen "Min-header".
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/casing-aa")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val headers = klient.get<String>(URI.create("${wiremock.baseUrl()}/casing-aa")) {
                addHeader("min-header", "forste")
                addHeader("Min-header", "siste")
            }.getOrFail().metadata.requestHeaders

            headers.keys shouldContain "Min-header"
            headers.keys shouldNotContain "min-header"
            headers["Min-header"] shouldBe listOf("forste", "siste")
        }
    }

    @Test
    fun `responseHeader-aksessorene slår opp case-insensitivt`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/svar-headere")).willReturn(
                    aResponse().withStatus(200).withBody("ok")
                        .withHeader("X-Enkelt", "verdi")
                        .withHeader("X-Multi", "a", "b"),
                ),
            )
            val klient = testHttpKlient()

            val metadata = klient.get<String>(URI.create("${wiremock.baseUrl()}/svar-headere")).getOrFail().metadata

            metadata.responseHeader("x-enkelt") shouldBe "verdi"
            metadata.responseHeader("X-ENKELT") shouldBe "verdi"
            metadata.responseHeaderValues("x-multi") shouldBe listOf("a", "b")
            metadata.responseHeader("finnes-ikke") shouldBe null
            metadata.responseHeaderValues("finnes-ikke").shouldBeEmpty()
        }
    }

    @Test
    fun `requestHeader-aksessorene slår opp case-insensitivt`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/req-headere")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val metadata = klient.get<String>(URI.create("${wiremock.baseUrl()}/req-headere")) {
                header("X-Trace-Id", "trace-1")
            }.getOrFail().metadata

            metadata.requestHeader("x-trace-id") shouldBe "trace-1"
        }
    }

    @Test
    fun `sensitive headere maskeres i rawRequestString men ikke i den strukturerte header-mappen`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/redaksjon")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val metadata = klient.get<String>(URI.create("${wiremock.baseUrl()}/redaksjon")) {
                header("authorization", "Bearer hemmelig-token")
                header("Cookie", "session=hemmelig-cookie")
            }.getOrFail().metadata

            // rawRequestString er beregnet for logging og maskerer derfor sensitive verdier.
            metadata.rawRequestString shouldContain "authorization: ***"
            metadata.rawRequestString shouldContain "Cookie: ***"
            metadata.rawRequestString shouldNotContain "hemmelig-token"
            metadata.rawRequestString shouldNotContain "hemmelig-cookie"
            // Den strukturerte mappen beholder ekte verdier slik at kallere kan inspisere dem programmatisk.
            metadata.requestHeaders["authorization"] shouldBe listOf("Bearer hemmelig-token")
            metadata.requestHeaders["Cookie"] shouldBe listOf("session=hemmelig-cookie")
        }
    }
}
