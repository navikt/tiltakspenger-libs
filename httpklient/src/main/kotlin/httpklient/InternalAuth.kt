package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.nå
import kotlin.time.Duration

/**
 * Resultatet av [resolveAuthToken]: det resolvede [token]et (eller `null` når ingen `Authorization` skal settes) sammen med [tidsstempler] som fanger når auth-provideren faktisk ble kalt.
 *
 * [tidsstempler] har kun [HttpKlientTidsstempler.authStartet]/[HttpKlientTidsstempler.authFullført] satt når en [AuthTokenProvider] faktisk ble kalt; ellers er den [HttpKlientTidsstempler.INGEN].
 */
internal data class ResolvedAuth(
    val token: AccessToken?,
    val tidsstempler: HttpKlientTidsstempler,
)

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
 * - `Right(ResolvedAuth(token = null, …))` betyr "ingen auth-header skal settes" (konsumenten styrer selv, eller ingen provider).
 * - `Left` returneres hvis provideren kaster.
 *
 * [ResolvedAuth.tidsstempler] fanger når provideren faktisk ble kalt, og er [HttpKlientTidsstempler.INGEN] når ingen provider ble kalt.
 *
 * Se [AuthTokenProvider] for hvordan [skipCache] brukes til skip-cache-retry ved f.eks. `401`.
 */
internal suspend fun HttpKlientRequest.resolveAuthToken(
    config: HttpKlient.HttpKlientConfig,
    skipCache: Boolean,
): Either<HttpKlientError.AuthError, ResolvedAuth> {
    authToken?.let { return ResolvedAuth(it, HttpKlientTidsstempler.INGEN).right() }
    val provider = effectiveAuthProvider(config) ?: return ResolvedAuth(null, HttpKlientTidsstempler.INGEN).right()
    val authStartet = nå(config.clock)
    return Either.catch { provider.hentToken(skipCache) }.mapLeft { e ->
        val authFullført = nå(config.clock)
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
                tidsstempler = HttpKlientTidsstempler(authStartet = authStartet, authFullført = authFullført, requestSendt = null, responsMottatt = null),
            ),
        )
    }.map { token ->
        ResolvedAuth(token, HttpKlientTidsstempler(authStartet = authStartet, authFullført = nå(config.clock), requestSendt = null, responsMottatt = null))
    }
}
