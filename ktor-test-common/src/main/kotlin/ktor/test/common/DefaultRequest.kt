package no.nav.tiltakspenger.libs.ktor.test.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
 * Sender en request via [defaultRequest] og asserter responsen mot [forventet].
 * Er [forventet] `null`, gjøres ingen assertions i det hele tatt.
 * Kryssjekker at manglende Content-Type innebærer tom responsbody.
 * Ved assertion-feil dumpes status, Content-Type og body i feilmeldingen.
 * For [ForventetBody.Bytes] dumpes byte-antallet i stedet for body-teksten.
 */
suspend fun ApplicationTestBuilder.defaultRequestWithAssertions(
    method: HttpMethod,
    uri: String,
    clock: Clock = fixedClock,
    jwt: String? = JwtGenerator(clock = clock).createJwtForSaksbehandler(),
    forventet: ForventetRespons?,
    setup: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    val response = defaultRequest(method = method, uri = uri, clock = clock, jwt = jwt, setup = setup)
    if (forventet == null) {
        return response
    }
    val bodyAsText = response.bodyAsText()
    val contentType = response.contentType()
    val status = response.status
    val bodyIFeilmelding = when (forventet.body) {
        is ForventetBody.Bytes -> "<${response.readRawBytes().size} byte>"
        else -> bodyAsText
    }
    withClue(
        "Response details:\n" +
            "Status: $status\n" +
            "Content-Type: $contentType\n" +
            "Body: $bodyIFeilmelding",
    ) {
        if (contentType == null) {
            bodyAsText shouldBe ""
        }
        status shouldBe forventet.status
        when (val forventetBody = forventet.body) {
            null -> {}

            ForventetBody.Tom -> {
                bodyAsText shouldBe ""
                contentType shouldBe null
            }

            is ForventetBody.Eksakt -> bodyAsText shouldBe forventetBody.verdi

            is ForventetBody.Json -> bodyAsText shouldEqualJson forventetBody.verdi

            is ForventetBody.Bytes -> response.readRawBytes() shouldBe forventetBody.verdi
        }
        if (forventet.contentType != null) {
            contentType shouldBe forventet.contentType
        }
    }
    return response
}
