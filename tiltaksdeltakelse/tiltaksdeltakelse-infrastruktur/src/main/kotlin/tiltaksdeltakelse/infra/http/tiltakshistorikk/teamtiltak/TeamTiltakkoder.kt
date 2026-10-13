package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TeamTiltakstatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.UgyldigKontraktsverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles.tiltakstype

// Kodene fra Team Tiltak: tiltakskodetabellene (flyttet fra toArenaKode i tiltakspenger-tiltak sin TiltakshistorikkMapper, men uten omveien om Arena-koden) og statusmappingen.
// En kjent kode blir Kjent, en ukjent blir kildens egen Ukjent-variant med kontraktens kode i behold — aldri valueOf, aldri kast; blank feiler som UgyldigKontraktsverdi.
// Innholdet er literal-pinnet i egne tester.

/**
 * Team Tiltak-koden som gir rett — kun arbeidstrening.
 */
internal val teamTiltakkoderSomGirRett: Map<String, TiltakstypeSomGirRett> = mapOf(
    "ARBEIDSTRENING" to TiltakstypeSomGirRett.ARBEIDSTRENING,
)

/**
 * Team Tiltak-kodene uten rett: lønnstilskuddene, mentor, inkluderingstilskudd, sommerjobb og VTAO.
 */
internal val kjenteTeamTiltakkoderUtenRett: Set<String> = setOf(
    "FIREARIG_LONNSTILSKUDD",
    "INKLUDERINGSTILSKUDD",
    "MENTOR",
    "MIDLERTIDIG_LONNSTILSKUDD",
    "SOMMERJOBB",
    "VARIG_LONNSTILSKUDD",
    "VTAO",
)

/**
 * Team Tiltak-kodene for lønnstilskudd, alle typer — en delmengde av [kjenteTeamTiltakkoderUtenRett].
 */
internal val teamTiltakkoderForLønnstilskudd: Set<String> = setOf(
    "FIREARIG_LONNSTILSKUDD",
    "MIDLERTIDIG_LONNSTILSKUDD",
    "VARIG_LONNSTILSKUDD",
)

internal fun teamTiltakTiltakstype(tiltakskode: String): Either<UgyldigKontraktsverdi, Tiltakstype> =
    tiltakstype(tiltakskode, teamTiltakkoderSomGirRett, kjenteTeamTiltakkoderUtenRett, teamTiltakkoderForLønnstilskudd, "Team Tiltak")

internal fun teamTiltakstatus(kodeIKontrakten: String): Either<UgyldigKontraktsverdi, TeamTiltakstatus> {
    val kjent = TeamTiltakstatus.Type.entries.find { it.name == kodeIKontrakten }
    return when {
        kjent != null -> TeamTiltakstatus.Kjent(kjent).right()
        kodeIKontrakten.isBlank() -> UgyldigKontraktsverdi("Blank avtalestatus fra Team Tiltak kan ikke bæres som ukjent kildeverdi").left()
        else -> TeamTiltakstatus.Ukjent(kodeIKontrakten).right()
    }
}
