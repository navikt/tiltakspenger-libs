package no.nav.tiltakspenger.libs.texas

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import no.nav.tiltakspenger.libs.auth.core.AdRolle
import no.nav.tiltakspenger.libs.common.GenerellSystembruker
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerrolle
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerroller
import no.nav.tiltakspenger.libs.common.Saksbehandler
import no.nav.tiltakspenger.libs.ktor.common.respond403Forbidden
import no.nav.tiltakspenger.libs.ktor.common.respond500InternalServerError

suspend fun ApplicationCall.saksbehandler(
    autoriserteBrukerroller: List<AdRolle>,
    systembrukerMapper: (klientId: String, klientnavn: String, roller: Set<String>) -> GenerellSystembruker<
        GenerellSystembrukerrolle,
        GenerellSystembrukerroller<GenerellSystembrukerrolle>,
        >,
): Saksbehandler? {
    val principal = principal<TexasPrincipalInternal>() ?: throw IllegalStateException("Mangler principal")

    principal.toSaksbehandler(autoriserteBrukerroller, systembrukerMapper).fold(
        ifLeft = {
            when (it) {
                is InternalPrincipalMappingfeil.IkkeSaksbehandler -> this.respond403Forbidden(
                    melding = "Brukeren er ikke en saksbehandler",
                    kode = "ikke_saksbehandler",
                )

                is InternalPrincipalMappingfeil.IngenRoller -> this.respond403Forbidden(
                    melding = "Saksbehandler må ha minst en autorisert rolle for å aksessere denne ressursen",
                    kode = "mangler_rolle",
                )

                is InternalPrincipalMappingfeil.ManglerClaim -> this.respond403Forbidden(
                    melding = "Tokenet mangler claim: ${it.claim}",
                    kode = "ugyldig_token",
                )

                else -> this.respond500InternalServerError(
                    melding = "Noe gikk galt ved mapping til saksbehandler",
                    kode = "ukjent_feil",
                )
            }
            return null
        },
        ifRight = {
            return it
        },
    )
}

suspend fun ApplicationCall.systembruker(
    systembrukerMapper: (klientId: String, klientnavn: String, roller: Set<String>) -> GenerellSystembruker<
        GenerellSystembrukerrolle,
        GenerellSystembrukerroller<GenerellSystembrukerrolle>,
        >,
): GenerellSystembruker<*, *>? {
    val principal = principal<TexasPrincipalInternal>() ?: throw IllegalStateException("Mangler principal")

    principal.toSystembruker(systembrukerMapper).fold(
        ifLeft = {
            when (it) {
                is InternalPrincipalMappingfeil.IkkeSystembruker -> this.respond403Forbidden(
                    melding = "Brukeren er ikke en systembruker",
                    kode = "ikke_systembruker",
                )

                is InternalPrincipalMappingfeil.IngenRoller -> this.respond403Forbidden(
                    melding = "Systembrukeren må ha minst en autorisert rolle for å aksessere denne ressursen",
                    kode = "mangler_rolle",
                )

                is InternalPrincipalMappingfeil.ManglerClaim -> this.respond403Forbidden(
                    melding = "Tokenet mangler claim: ${it.claim}",
                    kode = "ugyldig_token",
                )

                else -> this.respond500InternalServerError(
                    melding = "Noe gikk galt ved mapping til saksbehandler",
                    kode = "ukjent_feil",
                )
            }
            return null
        },
        ifRight = {
            return it
        },
    )
}
