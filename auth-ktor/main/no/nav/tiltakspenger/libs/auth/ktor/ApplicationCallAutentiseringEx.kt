package no.nav.tiltakspenger.libs.auth.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.nav.tiltakspenger.libs.auth.core.TokenService
import no.nav.tiltakspenger.libs.auth.core.Valideringsfeil
import no.nav.tiltakspenger.libs.common.Bruker
import no.nav.tiltakspenger.libs.common.Saksbehandler
import no.nav.tiltakspenger.libs.common.Systembruker
import no.nav.tiltakspenger.libs.ktor.common.respond401Unauthorized
import no.nav.tiltakspenger.libs.ktor.common.respond403Forbidden
import no.nav.tiltakspenger.libs.ktor.common.respond500InternalServerError
import kotlin.text.startsWith
import kotlin.text.substring

suspend inline fun ApplicationCall.withSaksbehandler(
    tokenService: TokenService,
    crossinline block: suspend (Saksbehandler) -> Unit,
) {
    return withBruker(tokenService) {
        if (it is Saksbehandler) {
            block(it)
        } else {
            this.respond403Forbidden(
                melding = "Brukeren er ikke en saksbehandler",
                kode = "ikke_saksbehandler",
            )
        }
    }
}

suspend inline fun ApplicationCall.withSystembruker(
    tokenService: TokenService,
    crossinline block: suspend (Systembruker) -> Unit,
) {
    return withBruker(tokenService) {
        if (it is Systembruker) {
            block(it)
        } else {
            this.respond403Forbidden(
                melding = "Brukeren er ikke en systembruker",
                kode = "ikke_systembruker",
            )
        }
    }
}

suspend inline fun ApplicationCall.withBruker(
    tokenService: TokenService,
    crossinline block: suspend (Bruker) -> Unit,
) {
    val authHeader = request.headers["Authorization"]
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/WWW-Authenticate
        this.response.headers.append("WWW-Authenticate", "Bearer realm=\"tiltakspenger-saksbehandling-api\"")
        this.respond(HttpStatusCode.Unauthorized)
    }
    val token = authHeader!!.substring(7)
    tokenService.validerOgHentBruker(token)
        .onLeft {
            when (it) {
                is Valideringsfeil.KunneIkkeHenteJwk -> this.respond500InternalServerError(
                    melding = "Feil ved henting av JWK. Denne requesten kan prøves på nytt.",
                    kode = "feil_ved_henting_av_jwk",
                )

                is Valideringsfeil.UgyldigToken -> this.respond401Unauthorized(
                    melding = "Ugyldig token. Se tiltakspenger-saksbehandling-api sine logger for mer detaljer.",
                    kode = "ugyldig_token",
                )

                is Valideringsfeil.UkjentFeil -> this.respond500InternalServerError(
                    melding = "Ukjent feil ved validering av token. Meld fra til #tiltakspenger-værsågod",
                    kode = "ukjent_feil_ved_validering_av_token",
                )
            }
        }
        .onRight {
            if (it.roller.isEmpty()) {
                this.respond403Forbidden(
                    melding = "Brukeren må ha minst en autorisert rolle for å aksessere denne ressursen",
                    kode = "mangler_rolle",
                )
            } else {
                block(it)
            }
        }
}
