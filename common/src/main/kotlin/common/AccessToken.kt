package no.nav.tiltakspenger.libs.common

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * @param token The access token. Typically used as a http-header. Authorization: Bearer <token>
 * @param expiresAt The time when the token expires.
 */
data class AccessToken(
    val token: String,
    val expiresAt: Instant,
) {
    /** Trekker fra 30 sekunders slingringsmonn. */
    fun remainingNanos(clock: Clock): Long = Duration.between(Instant.now(clock), expiresAt).minusSeconds(30).toNanos()
    override fun toString() = "Access token: <REDACTED>"
}
