package no.nav.tiltakspenger.libs.httpklient.infra

import io.github.oshai.kotlinlogging.KLogger
import io.mockk.mockk
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.httpklient.infra.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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
 * Standard test-klient med romslige timeouts som tåler treg CI uten å bli flaky.
 * Tester som faktisk verifiserer timeout-oppførsel overstyrer alltid timeout eksplisitt.
 * [transport] = `null` bruker klientens default (ekte [JavaHttpTransport] mot f.eks. WireMock); pipeline-tester sender inn en `FakeHttpTransport`.
 */
internal fun testHttpKlient(
    connectTimeout: Duration = 2.seconds,
    timeout: Duration = 10.seconds,
    auth: KlientAuth = KlientAuth.Ingen,
    retry: Retry = Retry.Ingen,
    circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig.None,
    skipCacheRetryStatuses: Set<Int> = setOf(401),
    clock: Clock = fixedClock,
    timeSource: TimeSource = TimeSource.Monotonic,
    transport: HttpTransport? = null,
): HttpKlient {
    val config = HttpKlientConfig(
        timeout = timeout,
        auth = auth,
        retry = retry,
        circuitBreaker = circuitBreaker,
        skipCacheRetryStatuses = skipCacheRetryStatuses,
        timeSource = timeSource,
    )
    // Uten transport-argument bygges produksjonstransporten med testens connect-timeout (connect-timeout bor på transporten, ikke i config).
    return HttpKlient(clock = clock, config = config, transport = transport ?: JavaHttpTransport(connectTimeout = connectTimeout))
}

/** Test-klient over en [FakeHttpTransport] — hele den reelle pipelinen kjører, kun nettverket er byttet ut. */
internal fun fakeHttpKlient(
    transport: FakeHttpTransport,
    auth: KlientAuth = KlientAuth.Ingen,
    retry: Retry = Retry.Ingen,
    circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig.None,
    skipCacheRetryStatuses: Set<Int> = setOf(401),
    clock: Clock = fixedClock,
    timeSource: TimeSource = TimeSource.Monotonic,
): HttpKlient = testHttpKlient(
    auth = auth,
    retry = retry,
    circuitBreaker = circuitBreaker,
    skipCacheRetryStatuses = skipCacheRetryStatuses,
    clock = clock,
    timeSource = timeSource,
    transport = transport,
)

/**
 * Innkapsler `mockk(relaxed = true)` for KLogger – logging kan ikke testes uten å observere logger, og KLogger har for mange metoder/overloads til at en håndlaget fake er praktisk.
 */
internal fun testLogger(): KLogger = mockk(relaxed = true)

/**
 * Tom [HttpKlientMetadata] for tester der innholdet ikke er relevant (f.eks. validerings-tester av statusCode-grenser).
 * Vi har ingen defaults i [HttpKlientMetadata] med vilje, så denne fyller ut alle felter eksplisitt med "ikke utført"-verdier.
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
    tidsstempler = HttpKlientTidsstempler.INGEN,
)

internal fun testAccessToken(
    token: String,
    clock: Clock = fixedClock,
): AccessToken = AccessToken(
    token = token,
    expiresAt = clock.instant().plusSeconds(60),
)

/**
 * Test-adapter som lager en [AuthTokenProvider] fra en lambda.
 * [AuthTokenProvider] er bevisst et vanlig interface (se KDoc der) slik at produksjons-wiring må navngi `skipCache`; i tester holder det å adaptere en lambda.
 */
internal fun authTokenProvider(hent: suspend (skipCache: Boolean) -> AccessToken): AuthTokenProvider =
    object : AuthTokenProvider {
        override suspend fun hentToken(skipCache: Boolean): AccessToken = hent(skipCache)
    }
