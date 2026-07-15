package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Header
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.requestHeader
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientPostTest {
    private data class LagretDto(val lagret: Boolean)

    @Test
    fun `postJson med SerialisertJson sender verbatim og deserialiserer responsen`() = runTest {
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

            val response = klient.postJson<LagretDto>(
                uri = URI.create("${wiremock.baseUrl()}/echo"),
                body = SerialisertJson("""{"navn":"test"}"""),
                headere = listOf(Header("X-Test", "1")),
            ).getOrFail()

            response.body shouldBe LagretDto(lagret = true)
            response.metadata.rawRequestString shouldBe """POST ${wiremock.baseUrl()}/echo
                |X-Test: 1
                |Content-Type: application/json
                |Accept: application/json
                |
                |{"navn":"test"}
            """.trimMargin()
            response.metadata.rawResponseString shouldBe """{"lagret":true}"""
            wiremock.verify(
                1,
                postRequestedFor(urlEqualTo("/echo"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withHeader("X-Test", equalTo("1"))
                    .withRequestBody(equalToJson("""{"navn":"test"}""")),
            )
        }
    }

    @Test
    fun `postJson serialiserer request-dto og deserialiserer response-dto`() = runTest {
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

            val response = klient.postJson<TestResponseDto>(
                uri = URI.create("${wiremock.baseUrl()}/dto-til-dto"),
                body = TestRequestDto(id = "abc", antall = 4),
            ).getOrFail()

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
    fun `postJsonUtenSvar sender DTO som JSON og ignorerer respons-bodyen typemessig`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/p")).willReturn(aResponse().withStatus(200).withBody("ignorert tekst")))
            val klient = testHttpKlient()

            val response = klient.postJsonUtenSvar(URI.create("${wiremock.baseUrl()}/p"), TestRequestDto(id = "abc", antall = 1)).getOrFail()

            response.body shouldBe Unit
            // Bodyen fanges fortsatt lesbart i metadata selv om den ignoreres typemessig.
            response.rawResponseString shouldBe "ignorert tekst"
            wiremock.verify(
                postRequestedFor(urlEqualTo("/p"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"id":"abc","antall":1}""")),
            )
        }
    }

    @Test
    fun `postJsonUtenSvar setter ikke Accept-header`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/uten-accept")).willReturn(aResponse().withStatus(204)))
            val klient = testHttpKlient()

            klient.postJsonUtenSvar(URI.create("${wiremock.baseUrl()}/uten-accept"), TestRequestDto(id = "a", antall = 1)).getOrFail()

            wiremock.verify(
                postRequestedFor(urlEqualTo("/uten-accept"))
                    .withoutHeader("Accept")
                    .withHeader("Content-Type", equalTo("application/json")),
            )
        }
    }

    @Test
    fun `postJson med SerialisertJson og kun defaults deserialiserer responsen`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/defaults-verbatim")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"lagret":true}"""),
                ),
            )
            val klient = testHttpKlient()

            klient.postJson<LagretDto>(URI.create("${wiremock.baseUrl()}/defaults-verbatim"), SerialisertJson("""{"a":1}""")).getOrFail()
                .body shouldBe LagretDto(lagret = true)
        }
    }

    @Test
    fun `postJson med SerialisertJson dobbel-serialiserer ikke`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/verbatim")).willReturn(aResponse().withStatus(204)))
            val klient = testHttpKlient()

            klient.postJsonUtenSvar(URI.create("${wiremock.baseUrl()}/verbatim"), SerialisertJson("""{"navn":"verbatim"}""")).getOrFail()

            // Hadde strengen blitt serialisert på nytt ville bodyen vært en JSON-string-literal ("{\"navn\":...}"), som ikke matcher dette JSON-objektet.
            wiremock.verify(
                postRequestedFor(urlEqualTo("/verbatim"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withRequestBody(equalToJson("""{"navn":"verbatim"}""")),
            )
        }
    }

    @Test
    fun `postTekst sender raa tekst med text-plain og deserialiserer json-respons`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/raw")).withHeader("Content-Type", containing("text/plain")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status":"ok","antall":1}"""),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.postTekst<TestResponseDto>(URI.create("${wiremock.baseUrl()}/raw"), tekst = "hei").getOrFail()

            response.body shouldBe TestResponseDto(status = "ok", antall = 1)
            // Jetty/WireMock normaliserer charset-casing på wire (utf-8 → UTF-8); charset-parameteren er case-insensitiv per RFC 2046.
            wiremock.verify(
                postRequestedFor(urlEqualTo("/raw"))
                    .withHeader("Content-Type", containing("text/plain"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withRequestBody(equalTo("hei")),
            )
            response.metadata.requestHeader("Content-Type") shouldBe "text/plain; charset=utf-8"
        }
    }

    @Test
    fun `postTekst med Unit ignorerer respons-bodyen og setter ikke Accept`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/tekst-unit")).willReturn(aResponse().withStatus(204)))
            val klient = testHttpKlient()

            val response = klient.postTekst<Unit>(URI.create("${wiremock.baseUrl()}/tekst-unit"), tekst = "12345678901").getOrFail()

            response.statusCode shouldBe 204
            wiremock.verify(
                postRequestedFor(urlEqualTo("/tekst-unit"))
                    .withoutHeader("Accept")
                    .withRequestBody(equalTo("12345678901")),
            )
        }
    }

    @Test
    fun `postForm url-koder felter og setter content-type`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/token")).willReturn(aResponse().withStatus(204)))
            val klient = testHttpKlient()

            klient.postForm<Unit>(
                uri = URI.create("${wiremock.baseUrl()}/token"),
                felter = listOf("grant_type" to "client_credentials", "scope" to "api://app x"),
            ).getOrFail()

            wiremock.verify(
                postRequestedFor(urlEqualTo("/token"))
                    .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                    .withRequestBody(equalTo("grant_type=client_credentials&scope=api%3A%2F%2Fapp+x")),
            )
        }
    }

    @Test
    fun `postForm bevarer gjentatte nøkler`() = runTest {
        // For application/x-www-form-urlencoded er gjentatte felter gyldige (f.eks. scope=a&scope=b), så liste-formen må ikke kollapse duplikater.
        withWireMockServer { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/scopes")).willReturn(aResponse().withStatus(204)))
            val klient = testHttpKlient()

            klient.postForm<Unit>(
                uri = URI.create("${wiremock.baseUrl()}/scopes"),
                felter = listOf("scope" to "a", "scope" to "b", "grant_type" to "client_credentials"),
            ).getOrFail()

            wiremock.verify(
                postRequestedFor(urlEqualTo("/scopes"))
                    .withRequestBody(equalTo("scope=a&scope=b&grant_type=client_credentials")),
            )
        }
    }

    @Test
    fun `postForm deserialiserer json-respons`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/token-json")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status":"token","antall":1}"""),
                ),
            )
            val klient = testHttpKlient()

            val response = klient.postForm<TestResponseDto>(
                uri = URI.create("${wiremock.baseUrl()}/token-json"),
                felter = listOf("grant_type" to "client_credentials"),
            ).getOrFail()

            response.body shouldBe TestResponseDto(status = "token", antall = 1)
        }
    }
}
