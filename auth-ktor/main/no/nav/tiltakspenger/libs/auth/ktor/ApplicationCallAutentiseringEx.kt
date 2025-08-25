package no.nav.tiltakspenger.libs.auth.ktor

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.nav.tiltakspenger.libs.auth.core.TokenService
import no.nav.tiltakspenger.libs.auth.core.Valideringsfeil
import no.nav.tiltakspenger.libs.common.Bruker
import no.nav.tiltakspenger.libs.common.GenerellSystembruker
import no.nav.tiltakspenger.libs.common.Saksbehandler
import no.nav.tiltakspenger.libs.ktor.common.respond401Unauthorized
import no.nav.tiltakspenger.libs.ktor.common.respond403Forbidden
import no.nav.tiltakspenger.libs.ktor.common.respond500InternalServerError
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import kotlin.text.startsWith
import kotlin.text.substring

@Deprecated("Bruk texas med tilhørende hjelpemetoder")
suspend inline fun ApplicationCall.withSaksbehandler(
    tokenService: TokenService,
    logger: KLogger = KotlinLogging.logger {},
    svarMed403HvisIngenSaksbehandlerRoller: Boolean = true,
    svarMed403HvisIngenScopes: Boolean = true,
    crossinline block: suspend (Saksbehandler) -> Unit,
) {
    return withBruker<Bruker<*, *>>(
        tokenService = tokenService,
        svarMed403HvisIngenSaksbehandlerRoller = svarMed403HvisIngenSaksbehandlerRoller,
        svarMed403HvisIngenScopes = svarMed403HvisIngenScopes,
        svarMed403HvisIngenSystembrukerRoller = false,
    ) {
        if (it is Saksbehandler) {
            block(it)
        } else {
            logger.warn { "Brukeren er ikke en saksbehandler. Svarer 403 Forbidden. Roller: ${it.roller}. NavIdent: ${it.navIdent}. Klientnavn: ${it.klientnavn}. KlientId: ${it.klientId}" }
            this.respond403Forbidden(
                melding = "Brukeren er ikke en saksbehandler",
                kode = "ikke_saksbehandler",
            )
        }
    }
}

@Deprecated("Bruk texas med tilhørende hjelpemetoder")
suspend inline fun <reified B : GenerellSystembruker<*, *>> ApplicationCall.withSystembruker(
    tokenService: TokenService,
    logger: KLogger = KotlinLogging.logger {},
    svarMed403HvisIngenSystembrukerRoller: Boolean = true,
    crossinline block: suspend (B) -> Unit,
) {
    // Scopes og saksbehandlerroller er kun aktuelt for on-behalf-og token.
    return withBruker<Bruker<*, *>>(
        tokenService = tokenService,
        svarMed403HvisIngenSaksbehandlerRoller = false,
        svarMed403HvisIngenScopes = false,
        svarMed403HvisIngenSystembrukerRoller = svarMed403HvisIngenSystembrukerRoller,
    ) {
        if (it is B) {
            block(it)
        } else {
            logger.warn { "Brukeren er ikke en systembruker. Svarer 403 Forbidden. Roller: ${it.roller}. NavIdent: ${it.navIdent}. Klientnavn: ${it.klientnavn}. KlientId: ${it.klientId}" }
            this.respond403Forbidden(
                melding = "Brukeren er ikke en systembruker",
                kode = "ikke_systembruker",
            )
        }
    }
}

@Deprecated("Bruk texas med tilhørende hjelpemetoder")
suspend inline fun <reified B : Bruker<*, *>> ApplicationCall.withBruker(
    tokenService: TokenService,
    logger: KLogger = KotlinLogging.logger {},
    svarMed403HvisIngenSaksbehandlerRoller: Boolean = true,
    svarMed403HvisIngenSystembrukerRoller: Boolean = true,
    svarMed403HvisIngenScopes: Boolean = true,
    crossinline block: suspend (B) -> Unit,
) {
    val authHeader = request.headers["Authorization"]
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/WWW-Authenticate
        this.response.headers.append("WWW-Authenticate", "Bearer realm=\"tiltakspenger-saksbehandling-api\"")
        this.respond(HttpStatusCode.Unauthorized)
        logger.warn { "Authorization header mangler eller er ikke av typen Bearer. Svarer 401 Unauthorized. Se sikkerlogg mer kontekst." }
        Sikkerlogg.warn { "Authorization header mangler eller er ikke av typen Bearer. Svarer 401 Unauthorized. Authorization header: $authHeader" }
        return
    }
    val token = authHeader.substring(7)
    tokenService.validerOgHentBruker(token)
        .onLeft {
            when (it) {
                // Alle disse skal logges i MicrosoftEntraIdTokenService
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
            when (it) {
                is Saksbehandler -> {
                    if (svarMed403HvisIngenSaksbehandlerRoller && it.roller.isEmpty()) {
                        logger.warn { "Brukeren har ingen forhåndsgodkjente roller. Svarer 403 Forbidden. NavIdent: ${it.navIdent}. Klientnavn: ${it.klientnavn}. KlientId: ${it.klientId}" }
                        this.respond403Forbidden(
                            melding = "Brukeren må ha minst en autorisert rolle for å aksessere denne ressursen",
                            kode = "mangler_rolle",
                        )
                        return
                    }
                    if (svarMed403HvisIngenScopes && it.scopes.isEmpty()) {
                        logger.warn { "Brukeren har ingen forhåndsgodkjente scopes. Svarer 403 Forbidden. NavIdent: ${it.navIdent}. Klientnavn: ${it.klientnavn}. KlientId: ${it.klientId}. Roller: ${it.roller}. Scopes: ${it.scopes}" }
                        this.respond403Forbidden(
                            melding = "Brukeren må ha minst en autorisert scope for å aksessere denne ressursen",
                            kode = "mangler_scope",
                        )
                    } else {
                        block(it as B)
                    }
                }

                is GenerellSystembruker -> {
                    if (svarMed403HvisIngenSystembrukerRoller && it.roller.isEmpty()) {
                        logger.warn { "Brukeren har ingen forhåndsgodkjente roller. Svarer 403 Forbidden. NavIdent: ${it.navIdent}. Klientnavn: ${it.klientnavn}. KlientId: ${it.klientId}" }
                        this.respond403Forbidden(
                            melding = "Brukeren må ha minst en autorisert rolle for å aksessere denne ressursen",
                            kode = "mangler_rolle",
                        )
                        return
                    }
                    block(it as B)
                }
            }
        }
}
