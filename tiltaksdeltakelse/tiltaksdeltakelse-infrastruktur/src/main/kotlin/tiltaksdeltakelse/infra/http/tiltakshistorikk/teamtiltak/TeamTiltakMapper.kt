package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak

import arrow.core.Either
import arrow.core.raise.either
import no.nav.tiltakspenger.libs.common.tilknytningstittel
import no.nav.tiltakspenger.libs.common.virksomhetsnavn
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Arrangør
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Deltakelsesomfang
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.EksternDeltakelseId
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Dto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.tiltaksdeltakelse

// Radmappingen fra Team Tiltak til domenet, uten prefiltrering: alt kilden ga oss får en variant, og fabrikken velger den.

internal fun TiltakshistorikkV1Dto.TeamTiltakAvtale.tilTiltaksdeltakelse(): Either<UgyldigKontraktsverdi, Tiltaksdeltakelse> = either {
    tiltaksdeltakelse(
        id = EksternDeltakelseId(id.toString()),
        kildestatus = teamTiltakstatus(status).bind(),
        tiltakstype = teamTiltakTiltakstype(tiltakstype.tiltakskode).bind(),
        tiltakstypenavn = tiltakstype.navn,
        tittel = tilknytningstittel(tittel),
        // Team Tiltaks avtale har én arbeidsgiver, som legges i underenhet — se KDoc på Arrangør i domenet.
        arrangør = Arrangør(
            hovedenhet = null,
            underenhet = virksomhetsnavn(arbeidsgiver.navn),
        ),
        omfang = Deltakelsesomfang(
            deltakelsesprosent = stillingsprosent,
            dagerPerUke = dagerPerUke,
            // Kontrakten har ingen gjennomføring for Team Tiltak-avtaler.
            deltidsprosentPåGjennomføring = null,
        ),
        fraOgMed = startDato,
        tilOgMed = sluttDato,
        gjennomføringId = null,
    )
}
