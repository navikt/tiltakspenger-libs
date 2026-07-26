package no.nav.tiltakspenger.libs.texas

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.GenerellSystembruker
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerrolle
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerroller
import no.nav.tiltakspenger.libs.common.Saksbehandler
import no.nav.tiltakspenger.libs.ktor.common.respond403Forbidden
import no.nav.tiltakspenger.libs.ktor.common.respond500InternalServerError

fun ApplicationCall.fnr(): Fnr {
    val principal = principal<TexasPrincipalExternalUser>() ?: throw IllegalStateException("Mangler principal")
    return principal.fnr
}

suspend fun ApplicationCall.saksbehandler(
    autoriserteBrukerroller: List<AdRolle>,
): Saksbehandler? {
    val principal = principal<TexasPrincipalInternal>() ?: throw IllegalStateException("Mangler principal")

    return principal.toSaksbehandler(autoriserteBrukerroller).fold(
        ifLeft = {
            this.respondMappingfeilForSaksbehandler(it)
            null
        },
        ifRight = { it },
    )
}

/**
 * Oversetter en mappingfeil fra [TexasPrincipalInternal.toSaksbehandler] til respons.
 * [InternalPrincipalMappingfeil.IkkeSystembruker] kan ikke oppstå i denne retningen, men when-et er uttømmende over sealed-interfacet slik at en ny variant blir en kompileringsfeil framfor en stille 500.
 */
internal suspend fun ApplicationCall.respondMappingfeilForSaksbehandler(feil: InternalPrincipalMappingfeil) {
    when (feil) {
        is InternalPrincipalMappingfeil.IkkeSaksbehandler -> {
            log.warn { "Mapping til saksbehandler feilet: Brukeren er ikke en saksbehandler" }
            this.respond403Forbidden(
                melding = "Brukeren er ikke en saksbehandler",
                kode = "ikke_saksbehandler",
            )
        }

        is InternalPrincipalMappingfeil.IngenRoller -> {
            log.warn { "Saksbehandler må ha minst en autorisert rolle for å aksessere denne ressursen" }
            this.respond403Forbidden(
                melding = "Saksbehandler må ha minst en autorisert rolle for å aksessere denne ressursen",
                kode = "mangler_rolle",
            )
        }

        is InternalPrincipalMappingfeil.ManglerClaim -> {
            log.warn { "Tokenet mangler claim: ${feil.claim}" }
            this.respond403Forbidden(
                melding = "Tokenet mangler claim: ${feil.claim}",
                kode = "ugyldig_token",
            )
        }

        is InternalPrincipalMappingfeil.IkkeSystembruker -> {
            log.warn { "Noe gikk galt ved mapping til saksbehandler" }
            this.respond500InternalServerError(
                melding = "Noe gikk galt ved mapping til saksbehandler",
                kode = "ukjent_feil",
            )
        }
    }
}

suspend fun ApplicationCall.systembruker(
    systembrukerMapper: (klientId: String, klientnavn: String, roller: Set<String>) -> GenerellSystembruker<
        GenerellSystembrukerrolle,
        GenerellSystembrukerroller<GenerellSystembrukerrolle>,
        >,
): GenerellSystembruker<*, *>? {
    val principal = principal<TexasPrincipalInternal>() ?: throw IllegalStateException("Mangler principal")

    return principal.toSystembruker(systembrukerMapper).fold(
        ifLeft = {
            this.respondMappingfeilForSystembruker(it)
            null
        },
        ifRight = { it },
    )
}

/**
 * Oversetter en mappingfeil fra [TexasPrincipalInternal.toSystembruker] til respons.
 * [InternalPrincipalMappingfeil.IkkeSaksbehandler] kan ikke oppstå i denne retningen, men when-et er uttømmende over sealed-interfacet slik at en ny variant blir en kompileringsfeil framfor en stille 500.
 */
internal suspend fun ApplicationCall.respondMappingfeilForSystembruker(feil: InternalPrincipalMappingfeil) {
    when (feil) {
        is InternalPrincipalMappingfeil.IkkeSystembruker -> {
            log.warn { "Mapping til systembruker feilet: Brukeren er ikke en systembruker" }
            this.respond403Forbidden(
                melding = "Brukeren er ikke en systembruker",
                kode = "ikke_systembruker",
            )
        }

        is InternalPrincipalMappingfeil.IngenRoller -> {
            log.warn { "Systembrukeren må ha minst en autorisert rolle for å aksessere denne ressursen" }
            this.respond403Forbidden(
                melding = "Systembrukeren må ha minst en autorisert rolle for å aksessere denne ressursen",
                kode = "mangler_rolle",
            )
        }

        is InternalPrincipalMappingfeil.ManglerClaim -> {
            log.warn { "Tokenet mangler claim: ${feil.claim}" }
            this.respond403Forbidden(
                melding = "Tokenet mangler claim: ${feil.claim}",
                kode = "ugyldig_token",
            )
        }

        is InternalPrincipalMappingfeil.IkkeSaksbehandler -> {
            log.warn { "Noe gikk galt ved mapping til systembruker" }
            this.respond500InternalServerError(
                melding = "Noe gikk galt ved mapping til systembruker",
                kode = "ukjent_feil",
            )
        }
    }
}
