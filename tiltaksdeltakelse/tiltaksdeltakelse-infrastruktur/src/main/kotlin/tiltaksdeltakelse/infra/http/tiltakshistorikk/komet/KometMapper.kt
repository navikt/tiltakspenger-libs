package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet

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

// Radmappingen fra Komet til domenet, uten prefiltrering: alt kilden ga oss får en variant, og fabrikken velger den.
// Tilsiktet avvik fra dagens kjede, som skyggekjøringen skal klassifisere: KLADD og andre utkast-statuser flyter inn i stedet for å filtreres bort.

internal fun TiltakshistorikkV1Dto.TeamKometDeltakelse.tilTiltaksdeltakelse(): Either<UgyldigKontraktsverdi, Tiltaksdeltakelse> = either {
    tiltaksdeltakelse(
        id = EksternDeltakelseId(id.toString()),
        kildestatus = kometstatus(
            kodeIKontrakten = status.type,
            årsakskode = status.aarsak,
            opprettet = status.opprettetDato,
        ).bind(),
        tiltakstype = kometTiltakstype(tiltakstype.tiltakskode).bind(),
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
