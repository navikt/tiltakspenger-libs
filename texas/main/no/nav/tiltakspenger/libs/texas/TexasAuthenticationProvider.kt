package no.nav.tiltakspenger.libs.texas

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.texas.client.TexasClient

val tillatteInnloggingsnivaer = listOf("idporten-loa-high", "Level4")
val log = KotlinLogging.logger("TexasAuth")

class TexasAuthenticationProvider(
    config: Config,
) : AuthenticationProvider(config) {
    class Config(
        name: String?,
        val identityProvider: IdentityProvider,
        val texasClient: TexasClient,
        val requireIdportenLevelHigh: Boolean = true,
    ) : AuthenticationProvider.Config(name)

    private val texasClient = config.texasClient
    private val identityProvider = config.identityProvider
    private val requireIdportenLevelHigh = config.requireIdportenLevelHigh

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val applicationCall = context.call
        val token =
            (applicationCall.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
                ?.takeIf { header -> header.authScheme.lowercase() == AuthScheme.Bearer.lowercase() }
                ?.blob

        if (token == null) {
            log.warn { "unauthenticated: no Bearer token found in Authorization header" }
            context.loginChallenge(AuthenticationFailedCause.NoCredentials)
            return
        }

        val introspectResponse =
            try {
                texasClient.introspectToken(token, identityProvider)
            } catch (e: Exception) {
                log.error { "unauthenticated: introspect request failed: ${e.message}" }
                context.loginChallenge(AuthenticationFailedCause.Error(e.message ?: "introspect request failed"))
                return
            }

        if (!introspectResponse.active) {
            log.warn { "unauthenticated: ${introspectResponse.error}" }
            context.loginChallenge(AuthenticationFailedCause.InvalidCredentials)
            return
        }

        val tokenClaims = introspectResponse.other

        val principal = when (identityProvider) {
            IdentityProvider.TOKENX -> context.getPrincipalForUser(tokenClaims, token) ?: return
            IdentityProvider.AZUREAD -> getPrincipalForSystem(tokenClaims, token)
            IdentityProvider.MASKINPORTEN -> context.loginChallenge(AuthenticationFailedCause.Error("Not implemented"))
            IdentityProvider.IDPORTEN -> context.loginChallenge(AuthenticationFailedCause.Error("Not implemented"))
        }
        context.principal(
            principal,
        )
    }

    private suspend fun AuthenticationContext.getPrincipalForUser(
        tokenClaims: Map<String, Any?>,
        token: String,
    ): TexasPrincipalUser? {
        val level = tokenClaims["acr"]?.toString()
        if (requireIdportenLevelHigh && (level == null || level !in tillatteInnloggingsnivaer)) {
            log.warn { "unauthenticated: må ha innloggingsnivå 4" }
            call.respond(HttpStatusCode.Unauthorized)
            return null
        }
        val fnrString = tokenClaims["pid"]?.toString()
        if (fnrString == null) {
            log.warn { "Fant ikke fnr i pid-claim" }
            call.respond(HttpStatusCode.InternalServerError)
            return null
        }
        val fnr = Fnr.fromString(fnrString)
        return TexasPrincipalUser(
            claims = tokenClaims,
            token = token,
            fnr = fnr,
        )
    }

    private fun getPrincipalForSystem(
        tokenClaims: Map<String, Any?>,
        token: String,
    ): TexasPrincipalSystem {
        return TexasPrincipalSystem(
            claims = tokenClaims,
            token = token,
        )
    }

    private fun AuthenticationContext.loginChallenge(cause: AuthenticationFailedCause) {
        challenge("Texas", cause) { authenticationProcedureChallenge, call ->
            call.respond(HttpStatusCode.Unauthorized)
            authenticationProcedureChallenge.complete()
        }
    }
}

fun ApplicationCall.fnr(): Fnr {
    val principal = principal<TexasPrincipalUser>() ?: throw IllegalStateException("Mangler principal")
    return principal.fnr
}

data class TexasPrincipalUser(
    val claims: Map<String, Any?>,
    val token: String,
    val fnr: Fnr,
)

data class TexasPrincipalSystem(
    val claims: Map<String, Any?>,
    val token: String,
)
