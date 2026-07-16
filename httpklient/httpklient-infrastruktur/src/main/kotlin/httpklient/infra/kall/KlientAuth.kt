package no.nav.tiltakspenger.libs.httpklient.infra.kall

import no.nav.tiltakspenger.libs.common.AccessToken

/**
 * Leverandør av auth-token til `HttpKlient`.
 *
 * Bevisst et vanlig interface (ikke en typealias eller `fun interface`): eksisterende wiring må da implementere [hentToken] eksplisitt og navngi skipCache-parameteren, i stedet for at en gammel parameterløs lambda kompilerer videre med en implisitt, ignorert `it`.
 * Det tvinger konsumenten til å ta stilling til parameteren når `libs` bumpes, slik at skip-cache-retryen faktisk videreformidler et ferskt token og ikke stille blir en noop (samme felle som den gamle `invaliderCache`-noopen).
 *
 * [hentToken] kalles med `skipCache = false` på det første forsøket for en request.
 * Hvis serveren svarer med en av [no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig.skipCacheRetryStatuses] (default `401`), gjør klienten ett nytt forsøk der [hentToken] kalles med `skipCache = true`, slik at et cachet token som ble avvist kan byttes ut med et ferskt.
 * Implementasjonen bestemmer selv hva skipCache betyr (typisk å tvinge fornyelse forbi en lokal token-cache, f.eks. `skip_cache` mot NAIS Texas).
 */
interface AuthTokenProvider {
    suspend fun hentToken(skipCache: Boolean): AccessToken
}

/**
 * Hvordan klienten setter `Authorization`-headeren.
 *
 * En per-kall `bearerToken`-parameter på metodene i `HttpKlient` overstyrer alltid dette (typisk OBO-tokens som veksles per saksbehandler).
 */
sealed interface KlientAuth {
    /** Ingen `Authorization`-header settes av klienten (pdfgen, ClamAV, leader-elector). */
    data object Ingen : KlientAuth

    /**
     * Systemtoken via [provider] (typisk Texas) foran hvert kall.
     * Ved avvist token (se [no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig.skipCacheRetryStatuses]) gjøres automatisk ett nytt forsøk med `hentToken(skipCache = true)`.
     */
    data class System(val provider: AuthTokenProvider) : KlientAuth
}
