package no.nav.tiltakspenger.libs.texas.client

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.texas.IdentityProvider

interface TexasClient {

    suspend fun introspectToken(
        token: String,
        identityProvider: IdentityProvider,
    ): TexasIntrospectionResponse

    /**
     * @param audienceTarget Target-appen tokenet skal utstedes for.
     * For Azure AD kan verdien oppgis både som nais-kortformen `cluster:namespace:app` og ferdig utskrevet `api://cluster.namespace.app/.default` — implementasjonen normaliserer selv, se [normaliserAzureTarget].
     * @param rewriteAudienceTarget Utgått og uten effekt; parameteret fjernes når ingen kallsteder oppgir det lenger.
     * Formen på target utledes nå av verdien selv, i stedet for av et flagg som måtte holdes i sync med nais-manifestet.
     */
    suspend fun getSystemToken(
        audienceTarget: String,
        identityProvider: IdentityProvider,
        rewriteAudienceTarget: Boolean = true,
        skipCache: Boolean = false,
    ): AccessToken

    /**
     * @param audienceTarget Target-appen tokenet skal veksles til.
     * Normaliseres som i [getSystemToken] når [identityProvider] er Azure AD (on-behalf-of), og sendes uendret for TokenX.
     */
    suspend fun exchangeToken(
        userToken: String,
        audienceTarget: String,
        identityProvider: IdentityProvider,
        skipCache: Boolean = false,
    ): AccessToken
}
