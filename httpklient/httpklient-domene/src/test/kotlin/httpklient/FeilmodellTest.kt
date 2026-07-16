package no.nav.tiltakspenger.libs.httpklient

import arrow.resilience.CircuitBreaker
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Feilmodellens rene domenesemantikk: retryable-flagg, gruppering, throwableOrNull/harStatus og hjelperne som bygger og logger feil.
 * Pipeline-oppførselen som PRODUSERER feilene testes i infrastruktur-modulen.
 */
internal class FeilmodellTest {
    private val io = IOException("boom")

    @Test
    fun `IngenRespons-variantene er retryable`() {
        HttpKlientError.Timeout(io, tomMetadata()).retryable.shouldBeTrue()
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
        HttpKlientError.Timeout(io, tomMetadata()).throwableOrNull() shouldBe io
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
        val feil = authFeilUtenKall(io)

        feil.throwable shouldBe io
        feil.metadata.attempts shouldBe 0
        feil.metadata.rawRequestString shouldBe ""
        feil.metadata.rawResponseString.shouldBeNull()
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
