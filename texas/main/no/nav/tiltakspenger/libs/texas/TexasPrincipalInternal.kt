package no.nav.tiltakspenger.libs.texas

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.auth.core.AdRolle
import no.nav.tiltakspenger.libs.common.GenerellSystembruker
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerrolle
import no.nav.tiltakspenger.libs.common.GenerellSystembrukerroller
import no.nav.tiltakspenger.libs.common.Saksbehandler
import no.nav.tiltakspenger.libs.common.Saksbehandlerroller

data class TexasPrincipalInternal(
    val claims: Map<String, Any?>,
    val token: String,
) {
    fun toSaksbehandler(
        autoriserteBrukerroller: List<AdRolle>,
    ): Either<InternalPrincipalMappingfeil, Saksbehandler> {
        val klientnavn =
            claims["azp_name"]?.toString() ?: return InternalPrincipalMappingfeil.ManglerClaim("azp_name").left()
        val klientId = claims["azp"]?.toString() ?: return InternalPrincipalMappingfeil.ManglerClaim("azp").left()
        if (claims["idtyp"]?.toString() == "app") {
            log.warn { "Brukeren er ikke en saksbehandler. Klientnavn: $klientnavn. KlientId: $klientId" }
            return InternalPrincipalMappingfeil.IkkeSaksbehandler.left()
        }
        val navIdent =
            claims["NAVident"]?.toString() ?: return InternalPrincipalMappingfeil.ManglerClaim("NAVident").left()
        val epost = claims["preferred_username"]?.toString()
            ?: return InternalPrincipalMappingfeil.ManglerClaim("preferred_username").left()
        val rollerFraClaim =
            claims["groups"]?.let { groups -> (groups as List<*>).map { it.toString() } } ?: emptyList()
        if (rollerFraClaim.isEmpty()) {
            log.warn { "Saksbehandler har ingen forhåndsgodkjente roller. NavIdent: $navIdent. Klientnavn: $klientnavn. KlientId: $klientId" }
            return InternalPrincipalMappingfeil.IngenRoller.left()
        }
        val roller = rollerFraClaim.mapNotNull {
            autoriserteBrukerroller.find { autorisertRolle -> it == autorisertRolle.objectId }?.name
        }.let { Saksbehandlerroller(it) }

        log.debug { "Mapping OK for saksbehandler $navIdent med roller $roller" }
        return Saksbehandler(
            navIdent = navIdent,
            brukernavn = epostToBrukernavn(epost),
            epost = epost,
            roller = roller,
            scopes = IngenSystembrukerroller(roller = emptySet()),
            klientId = klientId,
            klientnavn = klientnavn,
        ).right()
    }

    fun toSystembruker(
        systembrukerMapper: (klientId: String, klientnavn: String, roller: Set<String>) -> GenerellSystembruker<
            GenerellSystembrukerrolle,
            GenerellSystembrukerroller<GenerellSystembrukerrolle>,
            >,
    ): Either<InternalPrincipalMappingfeil, GenerellSystembruker<*, *>> {
        val klientnavn =
            claims["azp_name"]?.toString() ?: return InternalPrincipalMappingfeil.ManglerClaim("azp_name").left()
        val klientId = claims["azp"]?.toString() ?: return InternalPrincipalMappingfeil.ManglerClaim("azp").left()
        if (claims["idtyp"]?.toString() != "app") {
            log.warn { "Brukeren er ikke en systembruker. Klientnavn: $klientnavn. KlientId: $klientId" }
            return InternalPrincipalMappingfeil.IkkeSystembruker.left()
        }
        val rollerFraClaim =
            claims["roles"]?.let { roles -> (roles as List<*>).map { it.toString() } } ?: emptyList()
        if (rollerFraClaim.isEmpty()) {
            log.warn { "Systembruker har ingen forhåndsgodkjente roller. Klientnavn: $klientnavn. KlientId: $klientId" }
            return InternalPrincipalMappingfeil.IngenRoller.left()
        }
        val systembruker = rollerFraClaim.map {
            it.trim().lowercase()
        }.let { systembrukerMapper(klientId, klientnavn, it.toSet()) }

        log.debug { "Mapping OK for systembruker. KlientNavn: $klientnavn. KlientId: $klientId. Roller ${systembruker.roller}." }
        return systembruker.right()
    }
}

private fun epostToBrukernavn(epost: String): String = epost.split("@").first().replace(".", " ")

sealed interface InternalPrincipalMappingfeil {
    data class ManglerClaim(val claim: String) : InternalPrincipalMappingfeil
    data object IkkeSaksbehandler : InternalPrincipalMappingfeil
    data object IkkeSystembruker : InternalPrincipalMappingfeil
    data object IngenRoller : InternalPrincipalMappingfeil
}

// Brukes for mapping av saksbehandler for å slippe å sende inn systembrukermapper for saksbehandler
// siden saksbehandler uansett ikke har noen systembruker-roller
private data class IngenSystembrukerroller(
    override val value: Set<GenerellSystembrukerrolle>,
) : GenerellSystembrukerroller<GenerellSystembrukerrolle>,
    Set<GenerellSystembrukerrolle> by value {

    constructor(roller: Collection<GenerellSystembrukerrolle>) : this(roller.toSet())

    override fun harRolle(rolle: GenerellSystembrukerrolle): Boolean = contains(rolle)
}
