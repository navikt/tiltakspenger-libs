package no.nav.tiltakspenger.libs.httpklient

import arrow.resilience.CircuitBreaker
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import kotlin.time.Duration.Companion.seconds

/**
 * Feilmodellens rene domenesemantikk: retryable-flagg, gruppering, throwableOrNull/harStatus og hjelperne som bygger og logger feil.
 * Pipeline-oppførselen som PRODUSERER feilene testes i infrastruktur-modulen.
 */
internal class FeilmodellTest {
    private val io = IOException("boom")

    @Test
    fun `IngenRespons-variantene er retryable`() {
        HttpKlientError.Timeout(io, Timeoutfase.Svar, tomMetadata()).retryable.shouldBeTrue()
        HttpKlientError.NetworkError(io, tomMetadata()).retryable.shouldBeTrue()
    }

    @Test
    fun `RequestIkkeSendt-variantene er ikke retryable`() {
        HttpKlientError.InvalidRequest(io, tomMetadata()).retryable.shouldBeFalse()
        HttpKlientError.SerializationError(io, tomMetadata()).retryable.shouldBeFalse()
        HttpKlientError.AuthError(io, tomMetadata()).retryable.shouldBeFalse()
        HttpKlientError.CircuitBreakerOpen(
            mockk<CircuitBreaker.ExecutionRejected>(),
            tomMetadata(),
        ).retryable.shouldBeFalse()
    }

    @Test
    fun `UventetStatus er retryable kun for de forbigående statusene`() {
        listOf(408, 425, 429, 500, 502, 503, 504).forEach { status ->
            HttpKlientError.UventetStatus(status, "", tomMetadata(statusCode = status)).retryable.shouldBeTrue()
            isRetryableStatusCode(status).shouldBeTrue()
        }
        listOf(400, 401, 403, 404, 409, 501).forEach { status ->
            HttpKlientError.UventetStatus(status, "", tomMetadata(statusCode = status)).retryable.shouldBeFalse()
            isRetryableStatusCode(status).shouldBeFalse()
        }
    }

    @Test
    fun `DeserializationError er ikke retryable`() {
        HttpKlientError.DeserializationError(io, "body", 200, tomMetadata(statusCode = 200)).retryable.shouldBeFalse()
    }

    @Test
    fun `throwableOrNull gir underliggende exception for alle varianter unntatt UventetStatus`() {
        HttpKlientError.Timeout(io, Timeoutfase.Svar, tomMetadata()).throwableOrNull() shouldBe io
        HttpKlientError.InvalidRequest(io, tomMetadata()).throwableOrNull() shouldBe io
        HttpKlientError.DeserializationError(io, "body", 200, tomMetadata()).throwableOrNull() shouldBe io
        HttpKlientError.UventetStatus(500, "", tomMetadata()).throwableOrNull().shouldBeNull()
    }

    @Test
    fun `harStatus er sann kun for ResponsMottatt med en av statusene`() {
        val feil = HttpKlientError.UventetStatus(409, "duplikat", tomMetadata(statusCode = 409))

        feil.harStatus(409).shouldBeTrue()
        feil.harStatus(403, 409).shouldBeTrue()
        feil.harStatus(404).shouldBeFalse()
        HttpKlientError.NetworkError(io, tomMetadata()).harStatus(409).shouldBeFalse()
    }

    @Test
    fun `authFeilUtenKall gir AuthError med tom metadata og null forsøk`() {
        val feil = authFeilUtenKall(io, method = "POST", uri = URI.create("https://tilgangsmaskin.test/api/v1/komplett"))

        feil.throwable shouldBe io
        feil.metadata.attempts shouldBe 0
        feil.metadata.rawRequestString shouldBe ""
        feil.metadata.rawResponseString.shouldBeNull()
    }

    @Test
    fun `authFeilUtenKall bærer endepunktet videre, slik at feilloggen kan si hvor vi var på vei`() {
        val uri = URI.create("https://tilgangsmaskin.test/api/v1/komplett")

        authFeilUtenKall(io, method = "POST", uri = uri).endepunkt shouldBe "POST https://tilgangsmaskin.test/<skjult>"
        authFeilUtenKall(io, method = "POST", uri = uri, uriSynlighet = UriSynlighet.VanligLogg).endepunkt shouldBe
            "POST https://tilgangsmaskin.test/api/v1/komplett"
    }

    @Test
    fun `loggFeil logger med stacktrace når feilen bærer en throwable`() {
        val logger = testLogger()

        HttpKlientError.NetworkError(io, tomMetadata())
            .loggFeil(logger, operasjon = "sending til datadeling", kontekst = "Sak 123")

        verify(exactly = 1) { logger.error(io, any<() -> Any?>()) }
    }

    @Test
    fun `loggFeil logger uten stacktrace for UventetStatus`() {
        val logger = testLogger()

        HttpKlientError.UventetStatus(500, "", tomMetadata(statusCode = 500))
            .loggFeil(logger, operasjon = "sending", kontekst = "Sak 123")

        verify(exactly = 1) { logger.error(any<() -> Any?>()) }
    }

    /**
     * Kjernen i feilloggen: en asynkron feil fra `java.net.http` har ingen applikasjonsframes i stacktracen, så feilart og endepunkt MÅ stå i selve meldingen.
     * Testen pinner den formuleringen, ikke bare at det logges.
     */
    @Test
    fun `loggFeil navngir feilart, endepunkt og målinger i vanlig logg`() {
        val logger = testLogger()
        val melding = slot<() -> Any?>()

        HttpKlientError.Timeout(
            throwable = io,
            fase = Timeoutfase.Oppkobling,
            metadata = tomMetadata(
                method = "POST",
                uri = URI.create("https://skjermede-personer-pip.test/skjermet"),
                uriSynlighet = UriSynlighet.VanligLogg,
                tidsgrenser = Tidsgrenser(svar = 5.seconds, oppkobling = 3.seconds),
                attempts = 1,
            ),
        ).loggFeil(logger, operasjon = "skjermingsoppslag", kontekst = "Saksbehandler Z994321")

        verify(exactly = 1) { logger.error(io, capture(melding)) }
        melding.captured().toString() shouldBe
            "Feil ved skjermingsoppslag. Saksbehandler Z994321. Timeout (oppkobling, grense 3s) mot " +
            "POST https://skjermede-personer-pip.test/skjermet. forsøk: 1, brukt: 0s. Se sikkerlogg for mer kontekst."
    }

    @Test
    fun `loggFeil tar med status når serveren faktisk svarte, og skjuler URIen når klienten ikke har frikjent den`() {
        val logger = testLogger()
        val melding = slot<() -> Any?>()

        HttpKlientError.UventetStatus(
            statusCode = 503,
            body = "tjenesten er nede",
            metadata = tomMetadata(
                method = "GET",
                uri = URI.create("https://pdl.test/graphql?ident=12345678901"),
                statusCode = 503,
                attempts = 3,
            ),
        ).loggFeil(logger, operasjon = "personoppslag", kontekst = "Sak 123")

        verify(exactly = 1) { logger.error(capture(melding)) }
        melding.captured().toString() shouldBe
            "Feil ved personoppslag. Sak 123. Uventet HTTP-status 503 mot GET https://pdl.test/<skjult>. " +
            "status: 503, forsøk: 3, brukt: 0s. Se sikkerlogg for mer kontekst."
    }

    @Test
    fun `metadata-hjelperne slår opp headere case-insensitivt`() {
        val metadata = tomMetadata(
            requestHeaders = mapOf("X-Correlation-ID" to listOf("abc")),
            responseHeaders = mapOf("Content-Type" to listOf("application/json", "charset=utf-8")),
        )

        metadata.requestHeader("x-correlation-id") shouldBe "abc"
        metadata.requestHeaderValues("mangler") shouldBe emptyList()
        metadata.responseHeader("content-type") shouldBe "application/json"
        metadata.responseHeaderValues("CONTENT-TYPE") shouldBe listOf("application/json", "charset=utf-8")
        metadata.responseHeader("mangler").shouldBeNull()
    }

    @Test
    fun `metadata avviser negative attempts`() {
        shouldThrowWithMessage<IllegalArgumentException>("attempts kan ikke være negativ, var -1") {
            tomMetadata(attempts = -1)
        }
    }
}
