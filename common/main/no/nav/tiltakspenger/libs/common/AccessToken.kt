package no.nav.tiltakspenger.libs.common

import java.time.Duration
import java.time.Instant

/**
 * @param token The access token. Typically used as a http-header. Authorization: Bearer <token>
 * @param expiresAt The time when the token expires.
 * @param invaliderCache Tenkt brukt av klientene for å ha en mulighet til å slette cachen, dersom den tokenet ikke er gyldig lenger. Denne skal ikke kaste.
 */
data class AccessToken(
    val token: String,
    val expiresAt: Instant,
    val invaliderCache: () -> Unit,
) {
    /** Trekker fra 30 sekunders slingringsmonn. */
    fun remainingNanos(): Long = Duration.between(Instant.now(), expiresAt).minusSeconds(30).toNanos()
    override fun toString() = "Access token: <REDACTED>"
}
