package no.nav.tiltakspenger.libs.ktor.test.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.tiltakspenger.libs.auth.test.core.JwtGenerator
import no.nav.tiltakspenger.libs.common.fixedClock
import java.time.Clock

suspend fun ApplicationTestBuilder.defaultRequest(
    method: HttpMethod,
    uri: String,
    clock: Clock = fixedClock,
    jwt: String? = JwtGenerator(clock = clock).createJwtForSaksbehandler(),
    setup: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    return this.client.request(uri) {
        this.method = method
        this.headers {
            append(HttpHeaders.XCorrelationId, "DEFAULT_CALL_ID")
            append(HttpHeaders.ContentType, ContentType.Application.Json)
            if (jwt != null) append(HttpHeaders.Authorization, "Bearer $jwt")
        }
        setup()
    }
}

/**
 * Sender en request via [defaultRequest] og asserter på responsen.
 * Asserter alltid at status er [forventetStatus].
 * [forventetBody] asserter eksakt strenglikhet mot responsbodyen, mens [forventetJsonBody] asserter JSON-likhet.
 * Maks én av [forventetBody] og [forventetJsonBody] kan være satt.
 * [forventetContentType] asserter responsens Content-Type når den er satt; `null` betyr at Content-Type ikke assertes.
 * Kryssjekker at tom [forventetBody] innebærer at responsen ikke har Content-Type, og at manglende Content-Type innebærer tom responsbody.
 * Ved assertion-feil dumpes status, Content-Type og body i feilmeldingen.
 */
suspend fun ApplicationTestBuilder.defaultRequestWithAssertions(
    method: HttpMethod,
    uri: String,
    clock: Clock = fixedClock,
    jwt: String? = JwtGenerator(clock = clock).createJwtForSaksbehandler(),
    forventetStatus: HttpStatusCode,
    forventetBody: String? = null,
    forventetJsonBody: String? = null,
    forventetContentType: ContentType? = null,
    setup: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    require(forventetBody == null || forventetJsonBody == null) {
        "Sett maks én av forventetBody og forventetJsonBody"
    }
    val response = defaultRequest(method = method, uri = uri, clock = clock, jwt = jwt, setup = setup)
    val bodyAsText = response.bodyAsText()
    val contentType = response.contentType()
    val status = response.status
    withClue(
        "Response details:\n" +
            "Status: $status\n" +
            "Content-Type: $contentType\n" +
            "Body: $bodyAsText",
    ) {
        if (forventetBody == "") {
            contentType shouldBe null
        }
        if (contentType == null) {
            bodyAsText shouldBe ""
        }
        status shouldBe forventetStatus
        if (forventetBody != null) {
            bodyAsText shouldBe forventetBody
        }
        if (forventetJsonBody != null) {
            bodyAsText shouldEqualJson forventetJsonBody
        }
        if (forventetContentType != null) {
            contentType shouldBe forventetContentType
        }
    }
    return response
}
