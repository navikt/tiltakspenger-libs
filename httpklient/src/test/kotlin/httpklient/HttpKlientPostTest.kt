package no.nav.tiltakspenger.libs.httpklient
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientPostTest {
    @Test
    fun `request builder sender og mottar json som string`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/echo")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"lagret":true}"""),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.request<String>(URI.create("${wiremock.baseUrl()}/echo"), HttpMethod.POST) {
                header("X-Test", "1")
                json("""{"navn":"test"}""")
            }.getOrFail()

            response.body shouldBe """{"lagret":true}"""
            response.metadata.rawRequestString shouldBe """POST ${wiremock.baseUrl()}/echo
                |X-Test: 1
                |Content-Type: application/json
                |
                |{"navn":"test"}
            """.trimMargin()
            response.metadata.rawResponseString shouldBe """{"lagret":true}"""
            wiremock.verify(
                1,
                postRequestedFor(urlEqualTo("/echo"))
                    .withoutHeader("Accept")
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withHeader("X-Test", equalTo("1"))
                    .withRequestBody(equalToJson("""{"navn":"test"}""")),
            )
        }
    }

    @Test
    fun `post serialiserer request-dto og returnerer json som string`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/dto-til-string")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"mottatt":true}"""),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.post<String>(URI.create("${wiremock.baseUrl()}/dto-til-string")) {
                json(TestRequestDto(id = "abc", antall = 2))
            }.getOrFail()

            response.body shouldBe """{"mottatt":true}"""
            wiremock.verify(
                1,
                postRequestedFor(urlEqualTo("/dto-til-string"))
                    .withoutHeader("Accept")
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"id":"abc","antall":2}""")),
            )
        }
    }

    @Test
    fun `post serialiserer request-dto og deserialiserer response-dto`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/dto-til-dto")).willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Response", "ja")
                        .withBody("""{"status":"opprettet","antall":4}"""),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.post<TestResponseDto>(URI.create("${wiremock.baseUrl()}/dto-til-dto")) {
                json(TestRequestDto(id = "abc", antall = 4))
            }.getOrFail()

            response.statusCode shouldBe 201
            response.body shouldBe TestResponseDto(status = "opprettet", antall = 4)
            response.metadata.responseHeaders["X-Response"] shouldBe listOf("ja")
            response.metadata.requestHeaders["Accept"] shouldBe listOf("application/json")
            response.metadata.requestHeaders["Content-Type"] shouldBe listOf("application/json")
            wiremock.verify(
                1,
                postRequestedFor(urlEqualTo("/dto-til-dto"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"id":"abc","antall":4}""")),
            )
        }
    }

    @Test
    fun `body sender raw tekst med eksplisitte headere`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/raw")).withHeader("Content-Type", equalTo("text/plain")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("ok"),
                ),
            )
            val klient = testHttpKlient()

            klient.post<String>(URI.create("${wiremock.baseUrl()}/raw")) {
                acceptJson()
                contentTypeJson()
                header("Content-Type", "text/plain")
                body("hei")
            }.getOrFail().body shouldBe "ok"
        }
    }

    @Test
    fun `body-shorthand for post put patch sender DTO som JSON`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/p")).willReturn(aResponse().withStatus(200).withBody("ok")))
            wiremock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.put(urlEqualTo("/u"))
                    .willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            wiremock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.patch(urlEqualTo("/a"))
                    .willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()
            val dto = TestRequestDto(id = "abc", antall = 1)

            klient.post<String>(URI.create("${wiremock.baseUrl()}/p"), dto).getOrFail().body shouldBe "ok"
            klient.put<String>(URI.create("${wiremock.baseUrl()}/u"), dto).getOrFail().body shouldBe "ok"
            klient.patch<String>(URI.create("${wiremock.baseUrl()}/a"), dto).getOrFail().body shouldBe "ok"

            wiremock.verify(
                postRequestedFor(urlEqualTo("/p"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"id":"abc","antall":1}""")),
            )
            wiremock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor(urlEqualTo("/u"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"id":"abc","antall":1}""")),
            )
            wiremock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor(urlEqualTo("/a"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"id":"abc","antall":1}""")),
            )
        }
    }

    @Test
    fun `body-shorthand med extra builder kan overstyre headere`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/x")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            klient.post<String>(URI.create("${wiremock.baseUrl()}/x"), TestRequestDto(id = "id", antall = 2)) {
                header("X-Trace-Id", "trace-1")
            }.getOrFail()

            wiremock.verify(
                postRequestedFor(urlEqualTo("/x"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withHeader("X-Trace-Id", equalTo("trace-1"))
                    .withRequestBody(equalToJson("""{"id":"id","antall":2}""")),
            )
        }
    }

    @Test
    fun `body-shorthand med JSON-string sender verbatim uten dobbel-serialisering`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/verbatim")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            klient.post<String>(URI.create("${wiremock.baseUrl()}/verbatim"), """{"navn":"verbatim"}""").getOrFail()

            // Hadde strengen blitt serialisert på nytt ville bodyen vært en JSON-string-literal ("{\"navn\":...}"), som ikke matcher dette JSON-objektet.
            wiremock.verify(
                postRequestedFor(urlEqualTo("/verbatim"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"navn":"verbatim"}""")),
            )
        }
    }

    @Test
    fun `formUrlEncoded url-koder felter og setter content-type`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/token")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            klient.post<String>(URI.create("${wiremock.baseUrl()}/token")) {
                formUrlEncoded("grant_type" to "client_credentials", "scope" to "api://app x")
            }.getOrFail().body shouldBe "ok"

            wiremock.verify(
                postRequestedFor(urlEqualTo("/token"))
                    .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                    .withRequestBody(equalTo("grant_type=client_credentials&scope=api%3A%2F%2Fapp+x")),
            )
        }
    }

    @Test
    fun `formUrlEncoded med varargs bevarer gjentatte nøkler`() = runTest {
        // For application/x-www-form-urlencoded er gjentatte felter gyldige (f.eks. scope=a&scope=b), så varargs-formen må ikke kollapse duplikater via toMap().
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/scopes")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            klient.post<String>(URI.create("${wiremock.baseUrl()}/scopes")) {
                formUrlEncoded("scope" to "a", "scope" to "b", "grant_type" to "client_credentials")
            }.getOrFail().body shouldBe "ok"

            wiremock.verify(
                postRequestedFor(urlEqualTo("/scopes"))
                    .withRequestBody(equalTo("scope=a&scope=b&grant_type=client_credentials")),
            )
        }
    }

    @Test
    fun `body-shorthand med JSON-string for put og patch sender verbatim`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.put(urlEqualTo("/vu"))
                    .willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            wiremock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.patch(urlEqualTo("/va"))
                    .willReturn(aResponse().withStatus(200).withBody("ok")),
            )
            val klient = testHttpKlient()

            klient.put<String>(URI.create("${wiremock.baseUrl()}/vu"), """{"navn":"u"}""").getOrFail()
            klient.patch<String>(URI.create("${wiremock.baseUrl()}/va"), """{"navn":"a"}""").getOrFail()

            wiremock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor(urlEqualTo("/vu"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"navn":"u"}""")),
            )
            wiremock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor(urlEqualTo("/va"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"navn":"a"}""")),
            )
        }
    }

    @Test
    fun `formUrlEncoded med Map honorerer eksisterende content-type`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/form-map")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            klient.post<String>(URI.create("${wiremock.baseUrl()}/form-map")) {
                header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                formUrlEncoded(mapOf("a" to "1", "b" to "to ord"))
            }.getOrFail().body shouldBe "ok"

            wiremock.verify(
                postRequestedFor(urlEqualTo("/form-map"))
                    .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded; charset=UTF-8"))
                    .withRequestBody(equalTo("a=1&b=to+ord")),
            )
        }
    }

    @Test
    fun `formUrlEncoded honorerer eksisterende content-type med annen casing og lager ikke duplikat`() = runTest {
        // HTTP-headernavn er case-insensitive (RFC 9110 §5.1), så en allerede satt "content-type" skal forhindre at formUrlEncoded legger til en duplikat "Content-Type".
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/form-casing")).willReturn(aResponse().withStatus(200).withBody("ok")))
            val klient = testHttpKlient()

            val metadata = klient.post<String>(URI.create("${wiremock.baseUrl()}/form-casing")) {
                header("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                formUrlEncoded(mapOf("a" to "1"))
            }.getOrFail().metadata

            // Kun én content-type-header på tvers av casing, med konsumentens verdi beholdt.
            metadata.requestHeaders.keys.count { it.equals("Content-Type", ignoreCase = true) } shouldBe 1
            metadata.requestHeaderValues("content-type") shouldBe listOf("application/x-www-form-urlencoded; charset=UTF-8")
            wiremock.verify(
                postRequestedFor(urlEqualTo("/form-casing"))
                    .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded; charset=UTF-8")),
            )
        }
    }
}
