package no.nav.tiltakspenger.libs.texas.client

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.texas.IdentityProvider

/**
 * [AuthTokenProvider] som henter systemtokens fra Texas for én target-app.
 *
 * Erstatter de anonyme `AuthTokenProvider`-objektene appene tidligere wirer opp selv per klient: `HttpKlientConfig(auth = KlientAuth.System(TexasSystemTokenProvider(...)))`.
 * [skipCache][AuthTokenProvider.hentToken] videreformidles til Texas' `skip_cache`, slik at `httpklient` sin skip-cache-retry (typisk ved `401` fra target) faktisk tvinger fram et ferskt token forbi Texas-cachen.
 *
 * @param texasClient Klienten mot Texas-sidecaren.
 * @param audienceTarget Target-appen tokenet skal utstedes for (f.eks. `dev-gcp:tpts:tiltakspenger-datadeling`).
 * @param identityProvider Identity provideren tokenet skal utstedes av; default Entra ID.
 * @param rewriteAudienceTarget Se [TexasClient.getSystemToken] — skriver om `cluster:namespace:app` til `api://cluster.namespace.app/.default`.
 */
class TexasSystemTokenProvider(
    private val texasClient: TexasClient,
    private val audienceTarget: String,
    private val identityProvider: IdentityProvider = IdentityProvider.AZUREAD,
    private val rewriteAudienceTarget: Boolean = true,
) : AuthTokenProvider {
    override suspend fun hentToken(skipCache: Boolean): AccessToken =
        texasClient.getSystemToken(
            audienceTarget = audienceTarget,
            identityProvider = identityProvider,
            rewriteAudienceTarget = rewriteAudienceTarget,
            skipCache = skipCache,
        )
}
