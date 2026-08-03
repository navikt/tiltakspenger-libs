package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.httpklient.loggSuksess
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelser
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikkmeldinger
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjentDeltakelsesform
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjenteDeltakelsesformer
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.KanIkkeHenteIdenter
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.PdlIdentklient
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkKlient
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena.tilTiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.NorskIdentDto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Dto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Response
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.tiltakshistorikkmelding
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet.tilTiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak.tilTiltaksdeltakelse
import java.time.Clock

/**
 * Henter tiltakshistorikken for en person: identoppslag i PDL, oppslag mot `tiltakshistorikk`, og mapping til domenet.
 * Portert fra `TiltakshistorikkService` i `tiltakspenger-tiltak` som del av at appen avvikles — fallback-reglene for identoppslaget er bevart uendret.
 *
 * Klientene er stille og returnerer `Either`; denne tjenesten har domenekonteksten og logger derfor hver feilsituasjon nøyaktig én gang.
 * Suksess logges også — PII-fri linje i vanlig logg og rå respons i sikkerlogg — slik at sikkerlogg-dekningen fra dagens kjede bevares.
 *
 * Ingen prefiltrering: alt kilden ga oss flyter inn i domenets varianter, og et svar som ikke lar seg tolke feller hele oppslaget høylytt i stedet for at rader forsvinner stille.
 */
class TiltakshistorikkHenter(
    private val tiltakshistorikkKlient: TiltakshistorikkKlient,
    private val pdlIdentklient: PdlIdentklient,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg = Sikkerlogg,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun hentTiltakshistorikk(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<KunneIkkeHenteTiltakshistorikk, Tiltakshistorikk> {
        return hentIdenter(fnr).flatMap { identer ->
            tiltakshistorikkKlient.hentTiltakshistorikk(identer, correlationId)
                .mapLeft { feil ->
                    feil.loggFeil(
                        logger = logger,
                        operasjon = "henting av tiltakshistorikk",
                        kontekst = "Antall identer i oppslaget: ${identer.size}",
                        sikkerlogg = sikkerlogg,
                    )
                    KunneIkkeHenteTiltakshistorikk.KallFeilet
                }.flatMap { respons ->
                    respons.tilTiltakshistorikk(forespurteIdenter = identer)
                }
        }
    }

    /**
     * Kommentar John (portert fra tiltak-appen): I første omgang fallbacker vi bare til innsendt fnr for å få en myk overgang.
     * Lar denne feile ved null når vi har fjernet barnesykdommene.
     *
     * Fallbacken gjelder svarene der PDL ikke ga oss identer å bruke; feiler selve kallet, feiler også oppslaget vårt.
     * [KanIkkeHenteIdenter.UgyldigIdent] får samme fallback: et svar vi ikke stoler på brukes ikke, men innsendt fnr er fortsatt gyldig.
     */
    private suspend fun hentIdenter(fnr: Fnr): Either<KunneIkkeHenteTiltakshistorikk, Nel<Fnr>> {
        return pdlIdentklient.hentNåværendeOgHistoriskeFnr(fnr).fold(
            ifLeft = { feil ->
                feil.logg()
                when (feil) {
                    is KanIkkeHenteIdenter.KallFeilet -> KunneIkkeHenteTiltakshistorikk.IdentoppslagFeilet.left()

                    is KanIkkeHenteIdenter.GraphQLFeil,
                    is KanIkkeHenteIdenter.FantIngenIdenter,
                    is KanIkkeHenteIdenter.UgyldigIdent,
                    -> nonEmptyListOf(fnr).right()
                }
            },
            ifRight = { identer -> (if (fnr in identer) identer else identer + fnr).right() },
        )
    }

    /** Én logghendelse per feilsituasjon: vanlig logg uten personopplysninger, sikkerlogg med rå request/respons. */
    private fun KanIkkeHenteIdenter.logg() {
        when (this) {
            is KanIkkeHenteIdenter.KallFeilet -> httpKlientError.loggFeil(
                logger = logger,
                operasjon = "henting av identer fra PDL",
                kontekst = "Faller ikke tilbake til innsendt fnr; oppslaget feiler",
                sikkerlogg = sikkerlogg,
            )

            is KanIkkeHenteIdenter.GraphQLFeil -> {
                logger.error { "PDL svarte med GraphQL-feil ved henting av identer. Faller tilbake til innsendt fnr. ${sikkerlogg.seSikkerlogg}" }
                sikkerlogg.error { "PDL svarte med GraphQL-feil ved henting av identer: $feilmeldinger. Response: ${metadata.rawResponseString}" }
            }

            is KanIkkeHenteIdenter.FantIngenIdenter -> {
                logger.error { "Fant ingen identer i PDL. Faller tilbake til innsendt fnr. ${sikkerlogg.seSikkerlogg}" }
                sikkerlogg.error { "Fant ingen identer i PDL. Response: ${metadata.rawResponseString}" }
            }

            is KanIkkeHenteIdenter.UgyldigIdent -> {
                logger.error { "PDL svarte med en ident som ikke er et gyldig fødselsnummer. Faller tilbake til innsendt fnr. ${sikkerlogg.seSikkerlogg}" }
                sikkerlogg.error { "PDL svarte med en ident som ikke er et gyldig fødselsnummer. Response: ${metadata.rawResponseString}" }
            }
        }
    }

    private fun HttpKlientResponse<TiltakshistorikkV1Response>.tilTiltakshistorikk(
        forespurteIdenter: Nel<Fnr>,
    ): Either<KunneIkkeHenteTiltakshistorikk, Tiltakshistorikk> {
        return either<KunneIkkeHenteTiltakshistorikk.UgyldigRespons, Tiltakshistorikk> {
            val historikk = body.historikk

            // Integritetsvakt: hver rad skal tilhøre en av identene det ble spurt for — en fremmed ident kan være en annen persons data, og da skal ingenting flyte videre.
            val forespurte = forespurteIdenter.map { it.verdi }.toSet()
            historikk.forEach { rad ->
                val radIdent = rad.norskIdentEllerNull()
                ensure(radIdent == null || radIdent.verdi in forespurte) {
                    KunneIkkeHenteTiltakshistorikk.UgyldigRespons("Svaret inneholder en rad for en ident det ikke ble spurt om")
                }
            }

            val ukjenteFormer = historikk.filterIsInstance<TiltakshistorikkV1Dto.UkjentDeltakelse>().map { rad ->
                val type = rad.type
                ensure(!type.isNullOrBlank()) {
                    KunneIkkeHenteTiltakshistorikk.UgyldigRespons("Svaret inneholder en deltakelse uten type-diskriminator")
                }
                UkjentDeltakelsesform(type)
            }

            val deltakelser = historikk.mapNotNull { rad ->
                when (rad) {
                    is TiltakshistorikkV1Dto.ArenaDeltakelse -> rad.tilTiltaksdeltakelse().mapLeft { it.tilUgyldigRespons() }.bind()
                    is TiltakshistorikkV1Dto.TeamKometDeltakelse -> rad.tilTiltaksdeltakelse().mapLeft { it.tilUgyldigRespons() }.bind()
                    is TiltakshistorikkV1Dto.TeamTiltakAvtale -> rad.tilTiltaksdeltakelse().mapLeft { it.tilUgyldigRespons() }.bind()
                    is TiltakshistorikkV1Dto.UkjentDeltakelse -> null
                }
            }
            ensure(deltakelser.distinctBy { it.id }.size == deltakelser.size) {
                KunneIkkeHenteTiltakshistorikk.UgyldigRespons("Svaret inneholder dupliserte deltakelses-ider")
            }

            val meldinger = body.meldinger.map { kode ->
                tiltakshistorikkmelding(kode).mapLeft { it.tilUgyldigRespons() }.bind()
            }

            Tiltakshistorikk(
                deltakelser = Tiltaksdeltakelser(deltakelser),
                meldinger = Tiltakshistorikkmeldinger(meldinger),
                ukjenteDeltakelsesformer = UkjenteDeltakelsesformer(ukjenteFormer),
                hentetTidspunkt = nå(clock),
            )
        }.onLeft { feil ->
            logger.error { "Tiltakshistorikk-svaret kunne ikke tolkes: ${feil.beskrivelse}. ${sikkerlogg.seSikkerlogg}" }
            sikkerlogg.error { "Tiltakshistorikk-svaret kunne ikke tolkes: ${feil.beskrivelse}. Response: ${metadata.rawResponseString}" }
        }.onRight { historikk ->
            loggSuksess(
                logger = logger,
                melding = "Hentet tiltakshistorikk: ${historikk.deltakelser.deltakelser.size} deltakelser, ${historikk.meldinger.verdi.size} meldinger, ${historikk.ukjenteDeltakelsesformer.verdi.size} ukjente deltakelsesformer.",
                sikkerlogg = sikkerlogg,
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

private fun UgyldigKontraktsverdi.tilUgyldigRespons() = KunneIkkeHenteTiltakshistorikk.UgyldigRespons(beskrivelse)
