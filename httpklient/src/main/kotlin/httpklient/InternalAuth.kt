package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.common.AccessToken
import kotlin.time.Duration

/**
 * Provideren som faktisk skal styre `Authorization`-headeren for denne requesten, ellers `null`.
 *
 * Returnerer klientens [HttpKlient.HttpKlientConfig.authTokenProvider] kun når konsumenten _ikke_ selv styrer auth: verken en per-request [HttpKlientRequest.authToken] eller en eksplisitt `Authorization`-header er satt.
 * Er en av dem satt, styrer konsumenten headeren og vi returnerer `null` (og en skip-cache-retry gir ikke mening).
 *
 * Dette er den ene kilden til sannhet for «hvem styrer Authorization», delt mellom [resolveAuthToken] (som henter tokenet) og skip-cache-retryen i [executeWithSkipCacheRetry] (som kun aktiveres når provideren styrer headeren).
 */
internal fun HttpKlientRequest.effectiveAuthProvider(config: HttpKlient.HttpKlientConfig): AuthTokenProvider? {
    if (authToken != null || headers.containsAuthorizationHeader()) return null
    return config.authTokenProvider
}

/**
 * Resolver `Authorization`-tokenet for denne requesten.
 *
 * - En per-request [HttpKlientRequest.authToken] vinner alltid.
 * - Ellers henter [effectiveAuthProvider] via [AuthTokenProvider.hentToken] med [skipCache].
 * - `Right(null)` betyr "ingen auth-header skal settes" (konsumenten styrer selv, eller ingen provider).
 * - `Left` returneres hvis provideren kaster.
 *
 * Se [AuthTokenProvider] for hvordan [skipCache] brukes til skip-cache-retry ved f.eks. `401`.
 */
internal suspend fun HttpKlientRequest.resolveAuthToken(
    config: HttpKlient.HttpKlientConfig,
    skipCache: Boolean,
): Either<HttpKlientError.AuthError, AccessToken?> {
    authToken?.let { return it.right() }
    val provider = effectiveAuthProvider(config) ?: return null.right()
    return Either.catch { provider.hentToken(skipCache) }.mapLeft { e ->
        HttpKlientError.AuthError(
            throwable = e,
            metadata = HttpKlientMetadata(
                rawRequestString = rawRequestString(requestHeaders = headers, bodyAsString = null),
                rawResponseString = null,
                requestHeaders = headers,
                responseHeaders = emptyMap(),
                statusCode = null,
                attempts = 0,
                attemptDurations = emptyList(),
                totalDuration = Duration.ZERO,
            ),
        )
    }
}
