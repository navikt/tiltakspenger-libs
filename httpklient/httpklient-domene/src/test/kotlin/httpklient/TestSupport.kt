package no.nav.tiltakspenger.libs.httpklient

import io.github.oshai.kotlinlogging.KLogger
import io.mockk.mockk
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import kotlin.time.Duration.Companion.ZERO

/**
 * Tom [HttpKlientMetadata] for domene-tester der innholdet ikke er relevant.
 * Vi har ingen defaults i [HttpKlientMetadata] med vilje, så denne fyller ut alle felter eksplisitt med «ikke utført»-verdier.
 */
internal fun tomMetadata(
    rawRequestString: String = "",
    rawResponseString: String? = null,
    requestHeaders: Map<String, List<String>> = emptyMap(),
    responseHeaders: Map<String, List<String>> = emptyMap(),
    statusCode: Int? = null,
    attempts: Int = 0,
): HttpKlientMetadata = HttpKlientMetadata(
    rawRequestString = rawRequestString,
    rawResponseString = rawResponseString,
    requestHeaders = requestHeaders,
    responseHeaders = responseHeaders,
    statusCode = statusCode,
    attempts = attempts,
    attemptDurations = emptyList(),
    totalDuration = ZERO,
    tidsstempler = HttpKlientTidsstempler.INGEN,
)

/**
 * Innkapsler `mockk(relaxed = true)` for KLogger — logging kan ikke testes uten å observere logger, og KLogger har for mange metoder/overloads til at en håndlaget fake er praktisk.
 */
internal fun testLogger(): KLogger = mockk(relaxed = true)
