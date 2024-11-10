package no.nav.tiltakspenger.libs.auth.test.core

import com.auth0.jwk.JwkProvider
import no.nav.tiltakspenger.libs.auth.core.AdRolle
import no.nav.tiltakspenger.libs.auth.core.MicrosoftEntraIdTokenService
import no.nav.tiltakspenger.libs.auth.core.TokenService
import no.nav.tiltakspenger.libs.common.GenerellSystembruker
import no.nav.tiltakspenger.libs.common.Saksbehandlerrolle

fun tokenServiceForTest(
    jwtGenerator: JwtGenerator = JwtGenerator(),
    jwkProvider: JwkProvider = JwkFakeProvider(jwtGenerator.jwk),
    url: String = "unused",
    issuer: String = "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0",
    clientId: String = "c7adbfbb-1b1e-41f6-9b7a-af9627c04998",
    acceptIssuedAtLeeway: Long = 0,
    acceptNotBeforeLeeway: Long = 0,
    autoriserteBrukerroller: List<AdRolle> = Saksbehandlerrolle.entries.map { AdRolle(it, "ROLE_${it.name}") },
    systembrukerMapper: (String, Set<String>) -> GenerellSystembruker<*, *> = ::systembrukerMapperForTest,
): TokenService {
    return MicrosoftEntraIdTokenService(
        url = url,
        issuer = issuer,
        clientId = clientId,
        autoriserteBrukerroller = autoriserteBrukerroller,
        acceptIssuedAtLeeway = acceptIssuedAtLeeway,
        acceptNotBeforeLeeway = acceptNotBeforeLeeway,
        provider = jwkProvider,
        systembrukerMapper = systembrukerMapper,
    )
}
