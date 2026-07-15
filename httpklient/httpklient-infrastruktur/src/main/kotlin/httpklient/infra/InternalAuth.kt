package no.nav.tiltakspenger.libs.httpklient.infra

import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import java.time.Clock
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
 * Returnerer [KlientAuth.System]-provideren kun når konsumenten ikke har satt en per-kall [HttpKlientRequest.authToken]; er den satt, styrer konsumenten headeren og en skip-cache-retry gir ikke mening.
 * En eksplisitt `Authorization`-header finnes ikke lenger som mulighet — [no.nav.tiltakspenger.libs.httpklient.infra.kall.Header] avviser reserverte navn.
 *
 * Dette er den ene kilden til sannhet for «hvem styrer Authorization», delt mellom [resolveAuthToken] (som henter tokenet) og skip-cache-retryen i [executeWithSkipCacheRetry] (som kun aktiveres når provideren styrer headeren).
 */
internal fun HttpKlientRequest.effectiveAuthProvider(config: HttpKlientConfig): AuthTokenProvider? {
    if (authToken != null) return null
    return when (val auth = config.auth) {
        KlientAuth.Ingen -> null
        is KlientAuth.System -> auth.provider
    }
}

/**
 * Resolver `Authorization`-tokenet for denne requesten.
 *
 * - En per-kall [HttpKlientRequest.authToken] vinner alltid.
 * - Ellers henter [effectiveAuthProvider] via [AuthTokenProvider.hentToken] med [skipCache].
 * - `Right(ResolvedAuth(token = null, …))` betyr "ingen auth-header skal settes" (ingen provider konfigurert).
 * - `Left` returneres hvis provideren kaster.
 *
 * [ResolvedAuth.tidsstempler] fanger når provideren faktisk ble kalt, og er [HttpKlientTidsstempler.INGEN] når ingen provider ble kalt.
 *
 * Se [AuthTokenProvider] for hvordan [skipCache] brukes til skip-cache-retry ved f.eks. `401`.
 */
internal suspend fun HttpKlientRequest.resolveAuthToken(
    config: HttpKlientConfig,
    clock: Clock,
    skipCache: Boolean,
): Either<HttpKlientError.AuthError, ResolvedAuth> {
    authToken?.let { return ResolvedAuth(it, HttpKlientTidsstempler.INGEN).right() }
    val provider = effectiveAuthProvider(config) ?: return ResolvedAuth(null, HttpKlientTidsstempler.INGEN).right()
    val authStartet = nå(clock)
    return Either.catch { provider.hentToken(skipCache) }.mapLeft { e ->
        val authFullført = nå(clock)
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
        ResolvedAuth(token, HttpKlientTidsstempler(authStartet = authStartet, authFullført = nå(clock), requestSendt = null, responsMottatt = null))
    }
}
