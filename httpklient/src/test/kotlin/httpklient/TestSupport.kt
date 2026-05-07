package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.oshai.kotlinlogging.KLogger
import io.mockk.mockk
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.fixedClock
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

internal data class TestRequestDto(
    val id: String,
    val antall: Int,
)

internal data class TestResponseDto(
    val status: String,
    val antall: Int,
)

internal class SelvRefererendeDto {
    @Suppress("unused")
    val self: SelvRefererendeDto = this
}

/**
 * Standard test-klient med korte timeouts. Overstyr ved behov per test.
 */
internal fun testHttpKlient(
    connectTimeout: Duration = 100.milliseconds,
    timeout: Duration = 500.milliseconds,
    successStatus: (Int) -> Boolean = HttpStatusSuccess.is2xx,
    loggingConfig: HttpKlientLoggingConfig = HttpKlientLoggingConfig.Disabled,
    retryConfig: RetryConfig = RetryConfig.None,
    circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig.None,
    clock: Clock = fixedClock,
): HttpKlient = HttpKlient(clock = clock) {
    this.connectTimeout = connectTimeout
    this.defaultTimeout = timeout
    this.successStatus = successStatus
    this.logging = loggingConfig
    this.defaultRetry = retryConfig
    this.defaultCircuitBreaker = circuitBreakerConfig
}

/**
 * Starter en wiremock-server, gir den til [block], og stopper den uansett om [block] feiler.
 */
internal suspend fun <T> withWireMock(block: suspend (WireMockServer) -> T): T {
    val server = WireMockServer(0)
    server.start()
    return try {
        block(server)
    } finally {
        server.stop()
    }
}

/**
 * Starter en wiremock-server, henter base-url, stopper serveren igjen og returnerer en URI som peker
 * mot en port der ingen lytter. Brukes for å teste NetworkError ved "server ikke kontaktbar".
 */
internal fun stoppedServerUri(path: String): URI {
    val server = WireMockServer(0)
    server.start()
    val uri = URI.create("${server.baseUrl()}$path")
    server.stop()
    return uri
}

/**
 * Innkapsler `mockk(relaxed = true)` for KLogger – logging kan ikke testes uten å observere logger,
 * og KLogger har for mange metoder/overloads til at en håndlaget fake er praktisk.
 */
internal fun testLogger(): KLogger = mockk(relaxed = true)

/**
 * Tom [HttpKlientMetadata] for tester der innholdet ikke er relevant (f.eks. validerings-tester
 * av statusCode-grenser). Vi har ingen defaults i [HttpKlientMetadata] med vilje, så denne
 * fyller ut alle felter eksplisitt med "ikke utført"-verdier.
 */
internal fun tomMetadata(
    rawRequestString: String = "",
    rawResponseString: String? = null,
    requestHeaders: Map<String, List<String>> = emptyMap(),
    responseHeaders: Map<String, List<String>> = emptyMap(),
    statusCode: Int? = null,
): HttpKlientMetadata = HttpKlientMetadata(
    rawRequestString = rawRequestString,
    rawResponseString = rawResponseString,
    requestHeaders = requestHeaders,
    responseHeaders = responseHeaders,
    statusCode = statusCode,
    attempts = 0,
    attemptDurations = emptyList(),
    totalDuration = ZERO,
)

internal fun testAccessToken(
    token: String,
    clock: Clock = fixedClock,
): AccessToken = AccessToken(
    token = token,
    expiresAt = clock.instant().plusSeconds(60),
    invaliderCache = {},
)
