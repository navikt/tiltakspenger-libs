package no.nav.tiltakspenger.libs.httpklient

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class HttpKlientConvenienceAccessorsTest {
    private val metadata = HttpKlientMetadata(
        rawRequestString = "GET https://example.com/api",
        rawResponseString = "respons-body",
        requestHeaders = mapOf("Accept" to listOf("application/json")),
        responseHeaders = mapOf("Content-Type" to listOf("application/json")),
        statusCode = 503,
        attempts = 3,
        attemptDurations = listOf(100.milliseconds, 200.milliseconds, 700.milliseconds),
        totalDuration = 1.seconds,
        tidsstempler = HttpKlientTidsstempler.INGEN,
    )

    @Test
    fun `HttpKlientError eksponerer metadata-feltene via convenience-aksessorer`() {
        val error: HttpKlientError = HttpKlientError.Timeout(throwable = RuntimeException("timeout"), metadata = metadata)

        error.rawRequestString shouldBe metadata.rawRequestString
        error.rawResponseString shouldBe metadata.rawResponseString
        error.requestHeaders shouldBe metadata.requestHeaders
        error.responseHeaders shouldBe metadata.responseHeaders
        error.attempts shouldBe metadata.attempts
        error.attemptDurations shouldBe metadata.attemptDurations
        error.totalDuration shouldBe metadata.totalDuration
    }

    @Test
    fun `HttpKlientResponse eksponerer metadata-feltene via convenience-aksessorer`() {
        val response = HttpKlientResponse(statusCode = 200, body = "body", metadata = metadata)

        response.rawRequestString shouldBe metadata.rawRequestString
        response.rawResponseString shouldBe metadata.rawResponseString
        response.requestHeaders shouldBe metadata.requestHeaders
        response.responseHeaders shouldBe metadata.responseHeaders
        response.attempts shouldBe metadata.attempts
        response.attemptDurations shouldBe metadata.attemptDurations
        response.totalDuration shouldBe metadata.totalDuration
    }

    @Test
    fun `throwableOrNull gir throwable for feil som bærer en, ellers null`() {
        val throwable = RuntimeException("feil")

        val requestIkkeSendt: HttpKlientError = HttpKlientError.InvalidRequest(throwable = throwable, metadata = metadata)
        requestIkkeSendt.throwableOrNull() shouldBe throwable

        val ingenRespons: HttpKlientError = HttpKlientError.NetworkError(throwable = throwable, metadata = metadata)
        ingenRespons.throwableOrNull() shouldBe throwable

        val deserialisering: HttpKlientError =
            HttpKlientError.DeserializationError(throwable = throwable, body = "body", statusCode = 200, metadata = metadata)
        deserialisering.throwableOrNull() shouldBe throwable

        val uventetStatus: HttpKlientError =
            HttpKlientError.UventetStatus(statusCode = 500, body = "body", metadata = metadata)
        uventetStatus.throwableOrNull() shouldBe null
    }

    @Test
    fun `RequestBuilder eksponerer authToken som er satt`() {
        val token = testAccessToken("token-123")
        val builder = RequestBuilder(URI.create("https://example.com/api")).apply {
            bearerToken(token)
        }

        builder.authToken shouldBe token
    }
}
