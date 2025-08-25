package no.nav.tiltakspenger.libs.texas.client

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.tiltakspenger.libs.common.AccessToken
import java.time.Instant

data class TexasTokenRequest(
    @param:JsonProperty("identity_provider") val identityProvider: String,
    val target: String,
)

data class TexasExchangeTokenRequest(
    @param:JsonProperty("identity_provider") val identityProvider: String,
    val target: String,
    @param:JsonProperty("user_token") val userToken: String,
)

data class TexasTokenResponse(
    @param:JsonProperty("access_token")
    val accessToken: String,
    @param:JsonProperty("expires_in")
    val expiresInSeconds: Long,
) {
    fun toAccessToken(): AccessToken =
        AccessToken(
            token = accessToken,
            expiresAt = Instant.now().plusSeconds(expiresInSeconds),
        ) {}
}
