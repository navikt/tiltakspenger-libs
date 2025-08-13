package no.nav.tiltakspenger.libs.texas.client

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.texas.IdentityProvider

interface TexasClient {

    suspend fun introspectToken(
        token: String,
        identityProvider: IdentityProvider,
    ): TexasIntrospectionResponse

    suspend fun getSystemToken(
        audienceTarget: String,
        identityProvider: IdentityProvider,
    ): AccessToken

    suspend fun exchangeToken(
        userToken: String,
        audienceTarget: String,
        identityProvider: IdentityProvider,
    ): AccessToken
}
