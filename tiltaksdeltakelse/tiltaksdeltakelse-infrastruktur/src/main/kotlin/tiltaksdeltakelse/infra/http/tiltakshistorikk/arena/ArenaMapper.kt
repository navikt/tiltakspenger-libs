package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena

import arrow.core.Either
import arrow.core.raise.either
import no.nav.tiltakspenger.libs.common.tilknytningstittel
import no.nav.tiltakspenger.libs.common.virksomhetsnavn
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Arrangør
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Deltakelsesomfang
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.EksternDeltakelseId
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.GjennomføringId
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Dto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.tiltaksdeltakelse

// Radmappingen fra Arena til domenet, uten prefiltrering: alt kilden ga oss får en variant, og fabrikken velger den.
// Tilsiktede avvik fra dagens kjede, som skyggekjøringen skal klassifisere: datoer som ikke danner en periode blir Ugyldig i stedet for å forsvinne, kjente koder uten rett og ukjente koder flyter inn, og Arenas gjennomføring bæres (dagens mapper kastet den: id = "" og deltidsprosent = null).

internal fun TiltakshistorikkV1Dto.ArenaDeltakelse.tilTiltaksdeltakelse(): Either<UgyldigKontraktsverdi, Tiltaksdeltakelse> = either {
    tiltaksdeltakelse(
        // Kontraktens id er tiltakshistorikk-intern for Arena; TA-prefikset arenaId er nøkkelen i konsumentenes databaser og fra søknadsfronten.
        id = EksternDeltakelseId("TA$arenaId"),
        kildestatus = arenastatus(status).bind(),
        tiltakstype = arenaTiltakstype(tiltakstype.tiltakskode).bind(),
        tiltakstypenavn = tiltakstype.navn,
        tittel = tilknytningstittel(tittel),
        arrangør = Arrangør(
            hovedenhet = virksomhetsnavn(arrangor.hovedenhet?.navn),
            underenhet = virksomhetsnavn(arrangor.underenhet.navn),
        ),
        omfang = Deltakelsesomfang(
            deltakelsesprosent = deltidsprosent,
            dagerPerUke = dagerPerUke,
            deltidsprosentPåGjennomføring = gjennomforing.deltidsprosent,
        ),
        fraOgMed = startDato,
        tilOgMed = sluttDato,
        gjennomføringId = GjennomføringId(gjennomforing.id.toString()),
    )
}
