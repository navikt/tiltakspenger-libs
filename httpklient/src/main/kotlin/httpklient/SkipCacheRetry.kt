package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either

/**
 * Orkestrerer skip-cache-retryen beskrevet på [AuthTokenProvider] og [HttpKlient.HttpKlientConfig.authTokenProvider].
 *
 * [execute] kjøres først med `skipCache = false`.
 * Ett nytt forsøk med `skipCache = true` gjøres kun når _begge_ disse er sanne:
 *  - [providerStyrerAuthHeader] er `true` (dvs. `authTokenProvider` faktisk styrer `Authorization`-headeren), og
 *  - det første resultatet ble regnet som en _feil_ (`Left`) med en statuskode i [skipCacheRetryStatuses].
 *
 * At vi kun leser status fra en `Left` (og aldri fra en vellykket `Right`) er bevisst: en konsument som med vilje aksepterer f.eks. `401` som suksess via en custom `successStatus` skal ikke få et uventet ekstra HTTP-kall.
 *
 * Hvis også det ferske tokenet blir avvist, logges det som diagnostikk via [HttpKlientLoggingConfig.logSkipCacheRetryOppgitt] (styrt av [loggingConfig] på lik linje med resten av modulens logging).
 */
internal suspend fun executeWithSkipCacheRetry(
    request: HttpKlientRequest,
    providerStyrerAuthHeader: Boolean,
    skipCacheRetryStatuses: Set<Int>,
    loggingConfig: HttpKlientLoggingConfig,
    execute: suspend (skipCache: Boolean) -> Either<HttpKlientError, HttpKlientResponse<ByteArray>>,
): Either<HttpKlientError, HttpKlientResponse<ByteArray>> {
    val førsteResultat = execute(false)
    if (!providerStyrerAuthHeader) return førsteResultat

    val avvistStatus = førsteResultat.avvistStatusCode()
    if (avvistStatus == null || avvistStatus !in skipCacheRetryStatuses) return førsteResultat

    val andreResultat = execute(true)
    andreResultat.onLeft { error ->
        val andreStatus = error.metadata.statusCode
        if (andreStatus != null && andreStatus in skipCacheRetryStatuses) {
            // Et ferskt token ble også avvist — persistent avslag (typisk ABAC/tilgang), ikke et cache-problem.
            loggingConfig.logSkipCacheRetryOppgitt(request, error)
        }
    }
    return andreResultat
}

/**
 * Statuskoden fra et resultat som ble regnet som en feil (`Left`); `null` for et vellykket resultat (`Right`).
 * Se KDoc-en over for hvorfor vi bevisst ikke leser status fra en `Right`.
 */
private fun Either<HttpKlientError, HttpKlientResponse<ByteArray>>.avvistStatusCode(): Int? =
    fold(ifLeft = { it.metadata.statusCode }, ifRight = { null })
