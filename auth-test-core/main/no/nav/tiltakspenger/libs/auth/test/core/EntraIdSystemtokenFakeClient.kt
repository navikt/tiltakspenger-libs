package no.nav.tiltakspenger.libs.auth.test.core

import no.nav.tiltakspenger.libs.auth.core.EntraIdSystemtokenClient
import no.nav.tiltakspenger.libs.common.AccessToken
import java.time.Instant

class EntraIdSystemtokenFakeClient : EntraIdSystemtokenClient {

    /**
     * Returnerer en statisk [AccessToken], uavhengig av [otherAppId]. Utløper om en time. Ingen effekt dersom man kaller [AccessToken.invaliderCache].
     *
     * @param forceUpdateCache Ignorert i denne implementasjonen.
     */
    override suspend fun getSystemtoken(otherAppId: String, forceUpdateCache: Boolean): AccessToken {
        return AccessToken(
            token = "fake-token",
            expiresAt = Instant.now().plusSeconds(3600),
            invaliderCache = { },
        )
    }

    /** Ingen effekt i denne implementasjonen. */
    override fun invalidateToken(otherAppId: String) {}
}
