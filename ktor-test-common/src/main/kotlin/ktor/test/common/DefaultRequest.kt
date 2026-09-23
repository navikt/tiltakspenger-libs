package no.nav.tiltakspenger.libs.ktor.test.common

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.tiltakspenger.libs.auth.test.core.JwtGenerator
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import java.time.Clock
import io.ktor.http.HttpMethod as KtorHttpMethod

/**
 * Sender en request mot test-serveren med de headerne alle rutetestene våre trenger: call-id, Content-Type og et saksbehandler-token.
 *
 * `testApplication` kjører appen i minnet uten sokkel, så klienten den deler ut er eneste vei inn til rutene — det finnes ingen adresse `httpklient` kunne sendt til.
 * Derfor er ktor-klienten her bevisst, og innelukket: den står kun i denne fila, og kallstedene ser [TestRespons] og `httpklient`-vokabularet.
 *
 * @param method HTTP-metoden, fra `httpklient` sin [HttpMethod].
 * `DELETE`/`HEAD`/`OPTIONS` finnes ikke der, og heller ingen steder i flåten — trengs de, utvides enumen der.
 * @param body Requestens body, typisk JSON.
 * `null` sender ingen body, og da sendes heller ingen `Content-Type` — headeren beskriver bodyen, så uten body er den en løgn.
 * @param bodyContentType `Content-Type` for [body], som `String` slik `httpklient` også gjør det.
 * Har ingen virkning når [body] er `null`.
 * Ingen av rutene våre tar imot form-encoding eller multipart i dag, så JSON er default; trenger du noe annet, er det denne parameteren og ikke en ny body-type.
 * @param jwt Bearer-token som sendes i `Authorization`.
 * `null` sender requesten uten `Authorization`-header, f.eks. for å teste at en rute krever autentisering.
 * @param headere Headere som legges på requesten, og som overstyrer standardheaderne på samme navn.
 * Bruk konstantene i `io.ktor.http.HttpHeaders` eller de samme strengene som nøkler — overstyringen matcher på eksakt navn, så «authorization» ville blitt en ny header ved siden av `Authorization`.
 * Trengs sjelden: det vanlige er å la [jwt] styre `Authorization`, og dette er for tilfellene som tester selve header-parsingen.
 */
suspend fun ApplicationTestBuilder.defaultRequest(
    method: HttpMethod,
    uri: String,
    clock: Clock = fixedClock,
    jwt: String? = JwtGenerator(clock = clock).createJwtForSaksbehandler(),
    body: String? = null,
    bodyContentType: String = "application/json",
    headere: Map<String, String> = emptyMap(),
): TestRespons {
    require(bodyContentType.isNotBlank()) { "bodyContentType kan ikke være blank — den blir Content-Type-headeren på requesten." }
    val standardheadere = buildMap {
        put(HttpHeaders.XCorrelationId, "DEFAULT_CALL_ID")
        if (body != null) put(HttpHeaders.ContentType, bodyContentType)
        if (jwt != null) put(HttpHeaders.Authorization, "Bearer $jwt")
    }
    val response = this.client.request(uri) {
        this.method = method.tilKtor()
        this.headers {
            (standardheadere + headere).forEach { (navn, verdi) -> append(navn, verdi) }
        }
        if (body != null) setBody(body)
    }
    return TestRespons(
        method = method,
        sti = response.request.url.encodedPath,
        statusCode = response.status.value,
        contentType = response.contentType()?.toString(),
        body = response.bodyAsText(),
        bytes = response.readRawBytes(),
    )
}

/**
 * Sender en request via [defaultRequest] og asserter responsen mot [forventet].
 * Er [forventet] `null`, gjøres ingen assertions i det hele tatt.
 * Kryssjekker at manglende Content-Type innebærer tom responsbody.
 * Ved assertion-feil dumpes status, Content-Type og body i feilmeldingen.
 * For [ForventetBody.Bytes] dumpes byte-antallet i stedet for body-teksten.
 *
 * Konsumenter skal uttrykke forventningene sine gjennom [ForventetRespons] framfor å asserte på [TestRespons] selv.
 * Poenget er at alle rutetestene sjekker de samme tingene på samme måte, og feiler med den samme respons-dumpen.
 */
suspend fun ApplicationTestBuilder.defaultRequestWithAssertions(
    method: HttpMethod,
    uri: String,
    clock: Clock = fixedClock,
    jwt: String? = JwtGenerator(clock = clock).createJwtForSaksbehandler(),
    body: String? = null,
    bodyContentType: String = "application/json",
    headere: Map<String, String> = emptyMap(),
    forventet: ForventetRespons?,
): TestRespons {
    val respons = defaultRequest(
        method = method,
        uri = uri,
        clock = clock,
        jwt = jwt,
        body = body,
        bodyContentType = bodyContentType,
        headere = headere,
    )
    if (forventet == null) {
        return respons
    }
    val bodyIFeilmelding = when (forventet.body) {
        is ForventetBody.Bytes -> "<${respons.bytes.size} byte>"
        else -> respons.body
    }
    withClue(
        "Response details:\n" +
            "Status: ${respons.statusCode}\n" +
            "Content-Type: ${respons.contentType}\n" +
            "Body: $bodyIFeilmelding",
    ) {
        if (respons.contentType == null) {
            respons.body shouldBe ""
        }
        respons.statusCode shouldBe forventet.status
        when (val forventetBody = forventet.body) {
            null -> {}

            ForventetBody.Tom -> {
                respons.body shouldBe ""
                respons.contentType shouldBe null
            }

            is ForventetBody.Eksakt -> respons.body shouldBe forventetBody.verdi

            is ForventetBody.Json -> respons.body shouldEqualJson forventetBody.verdi

            is ForventetBody.Bytes -> respons.bytes shouldBe forventetBody.verdi
        }
        if (forventet.contentType != null) {
            respons.contentType shouldBe forventet.contentType
        }
    }
    return respons
}

/**
 * `httpklient` sin [HttpMethod] er vokabularet utad; ktor sin er en implementasjonsdetalj på vei inn i test-klienten.
 * Enumen er uttømmende, så en ny verdi der gir kompileringsfeil her i stedet for en runtime-overraskelse.
 */
private fun HttpMethod.tilKtor(): KtorHttpMethod = when (this) {
    HttpMethod.GET -> KtorHttpMethod.Get
    HttpMethod.POST -> KtorHttpMethod.Post
    HttpMethod.PUT -> KtorHttpMethod.Put
    HttpMethod.PATCH -> KtorHttpMethod.Patch
}
