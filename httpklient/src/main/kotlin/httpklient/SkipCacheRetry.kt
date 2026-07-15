package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either

/**
 * Orkestrerer skip-cache-retryen beskrevet på [AuthTokenProvider] og [HttpKlientConfig.skipCacheRetryStatuses].
 *
 * [execute] kjøres først med `skipCache = false`.
 * Ett nytt forsøk med `skipCache = true` gjøres kun når _begge_ disse er sanne:
 *  - [providerStyrerAuthHeader] er `true` (dvs. en [KlientAuth.System]-provider faktisk styrer `Authorization`-headeren), og
 *  - det første resultatet ble regnet som en _feil_ (`Left`) med en statuskode i [skipCacheRetryStatuses].
 *
 * At vi kun leser status fra en `Left` (og aldri fra en vellykket `Right`) er bevisst: en konsument som med vilje aksepterer f.eks. `401` som suksess via [Statusregel.Eksakt] skal ikke få et uventet ekstra HTTP-kall.
 */
internal suspend fun executeWithSkipCacheRetry(
    providerStyrerAuthHeader: Boolean,
    skipCacheRetryStatuses: Set<Int>,
    execute: suspend (skipCache: Boolean) -> Either<HttpKlientError, HttpKlientResponse<ByteArray>>,
): Either<HttpKlientError, HttpKlientResponse<ByteArray>> {
    val førsteResultat = execute(false)
    if (!providerStyrerAuthHeader) return førsteResultat

    val avvistStatus = førsteResultat.avvistStatusCode()
    if (avvistStatus == null || avvistStatus !in skipCacheRetryStatuses) return førsteResultat

    return execute(true)
}

/**
 * Statuskoden fra et resultat som ble regnet som en feil (`Left`); `null` for et vellykket resultat (`Right`).
 * Se KDoc-en over for hvorfor vi bevisst ikke leser status fra en `Right`.
 */
private fun Either<HttpKlientError, HttpKlientResponse<ByteArray>>.avvistStatusCode(): Int? =
    fold(ifLeft = { it.metadata.statusCode }, ifRight = { null })
