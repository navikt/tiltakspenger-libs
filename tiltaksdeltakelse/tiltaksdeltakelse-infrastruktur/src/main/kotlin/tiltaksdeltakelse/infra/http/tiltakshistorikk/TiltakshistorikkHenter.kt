package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelser
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjentDeltakelsesform
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjenteDeltakelsesformer
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.KanIkkeHenteIdenter
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.PdlIdentklient
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkKlient
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena.tilTiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.NorskIdentDto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Dto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Response
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet.tilTiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak.tilTiltaksdeltakelse
import java.time.Clock

/**
 * Henter tiltakshistorikken for en person: identoppslag i PDL, oppslag mot `tiltakshistorikk`, og mapping til domenet.
 * Portert fra `TiltakshistorikkService` i `tiltakspenger-tiltak` som del av at appen avvikles — fallback-reglene for identoppslaget er bevart uendret.
 *
 * **Tjenesten logger ikke.**
 * Den returnerer i stedet nok til at konsumenten kan gjøre det, på samme måte som `httpklient`: [KunneIkkeHenteTiltakshistorikk] bærer feilen med metadata, og [TiltakshistorikkResultat] bærer responsen og hvordan identene ble til.
 * Grunnen er at alvorligheten ikke er en egenskap ved hentingen, men ved hvem som venter på den: den samme feilen er en driftsfeil på den ekte veien og støy i en skyggekjøring.
 * Konsumenten har den konteksten, biblioteket har den ikke.
 *
 * Ingen prefiltrering: alt kilden ga oss flyter inn i domenets varianter, og et svar som ikke lar seg tolke feller hele oppslaget høylytt i stedet for at rader forsvinner stille.
 */
class TiltakshistorikkHenter(
    private val tiltakshistorikkKlient: TiltakshistorikkKlient,
    private val pdlIdentklient: PdlIdentklient,
    private val clock: Clock,
) {
    suspend fun hentTiltakshistorikk(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<KunneIkkeHenteTiltakshistorikk, TiltakshistorikkResultat> {
        return hentIdenter(fnr).flatMap { identoppslag ->
            tiltakshistorikkKlient.hentTiltakshistorikk(identoppslag.identer, correlationId)
                .mapLeft { KunneIkkeHenteTiltakshistorikk.KallFeilet(it) }
                .flatMap { respons ->
                    respons.tilTiltakshistorikk(forespurteIdenter = identoppslag.identer).map { historikk ->
                        TiltakshistorikkResultat(
                            respons = HttpKlientResponse(
                                statusCode = respons.statusCode,
                                body = historikk,
                                metadata = respons.metadata,
                            ),
                            identoppslag = identoppslag,
                        )
                    }
                }
        }
    }

    /**
     * Et PDL-kall som feilet feller hele oppslaget; et svar uten brukbare identer gjør det ikke.
     * Skillet er typet i [KanIkkeHenteIdenter], og fallbacken bæres videre på [Identoppslag] slik at konsumenten kan se at den skjedde.
     */
    private suspend fun hentIdenter(fnr: Fnr): Either<KunneIkkeHenteTiltakshistorikk, Identoppslag> {
        return pdlIdentklient.hentNåværendeOgHistoriskeFnr(fnr).fold(
            ifLeft = { feil ->
                when (feil) {
                    is KanIkkeHenteIdenter.KallFeilet ->
                        KunneIkkeHenteTiltakshistorikk.IdentoppslagFeilet(feil.httpKlientError).left()

                    is KanIkkeHenteIdenter.UtenBrukbareIdenter ->
                        Identoppslag.FaltTilbakeTilInnsendtFnr(identer = nonEmptyListOf(fnr), grunn = feil).right()
                }
            },
            ifRight = { identer ->
                Identoppslag.FraPdl(if (fnr in identer) identer else identer + fnr).right()
            },
        )
    }

    private fun HttpKlientResponse<TiltakshistorikkV1Response>.tilTiltakshistorikk(
        forespurteIdenter: Nel<Fnr>,
    ): Either<KunneIkkeHenteTiltakshistorikk.UgyldigRespons, Tiltakshistorikk> {
        // Metadataen er den rå responsen, som er det eneste som forteller hvilken rad som var gal — den følger derfor med hver eneste ugyldig-respons herfra.
        fun ugyldig(beskrivelse: String) = KunneIkkeHenteTiltakshistorikk.UgyldigRespons(beskrivelse = beskrivelse, metadata = metadata)

        return either {
            val historikk = body.historikk

            // Integritetsvakt: hver rad skal tilhøre en av identene det ble spurt for — en fremmed ident kan være en annen persons data, og da skal ingenting flyte videre.
            val forespurte = forespurteIdenter.map { it.verdi }.toSet()
            historikk.forEach { rad ->
                val radIdent = rad.norskIdentEllerNull()
                ensure(radIdent == null || radIdent.verdi in forespurte) {
                    ugyldig("Svaret inneholder en rad for en ident det ikke ble spurt om")
                }
            }

            val ukjenteFormer = historikk.filterIsInstance<TiltakshistorikkV1Dto.UkjentDeltakelse>().map { rad ->
                val type = rad.type
                ensure(!type.isNullOrBlank()) {
                    ugyldig("Svaret inneholder en deltakelse uten type-diskriminator")
                }
                UkjentDeltakelsesform(type)
            }

            val deltakelser = historikk.mapNotNull { rad ->
                when (rad) {
                    is TiltakshistorikkV1Dto.ArenaDeltakelse -> rad.tilTiltaksdeltakelse().mapLeft { ugyldig(it.beskrivelse) }.bind()
                    is TiltakshistorikkV1Dto.TeamKometDeltakelse -> rad.tilTiltaksdeltakelse().mapLeft { ugyldig(it.beskrivelse) }.bind()
                    is TiltakshistorikkV1Dto.TeamTiltakAvtale -> rad.tilTiltaksdeltakelse().mapLeft { ugyldig(it.beskrivelse) }.bind()
                    is TiltakshistorikkV1Dto.UkjentDeltakelse -> null
                }
            }
            ensure(deltakelser.distinctBy { it.id }.size == deltakelser.size) {
                ugyldig("Svaret inneholder dupliserte deltakelses-ider")
            }

            Tiltakshistorikk(
                deltakelser = Tiltaksdeltakelser(deltakelser),
                ukjenteDeltakelsesformer = UkjenteDeltakelsesformer(ukjenteFormer),
                hentetTidspunkt = nå(clock),
            )
        }
    }
}

private fun TiltakshistorikkV1Dto.norskIdentEllerNull(): NorskIdentDto? = when (this) {
    is TiltakshistorikkV1Dto.ArenaDeltakelse -> norskIdent
    is TiltakshistorikkV1Dto.TeamKometDeltakelse -> norskIdent
    is TiltakshistorikkV1Dto.TeamTiltakAvtale -> norskIdent
    is TiltakshistorikkV1Dto.UkjentDeltakelse -> null
}
