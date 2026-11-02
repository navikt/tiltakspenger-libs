package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles

import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakskilde
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.arena.arenakoderSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.komet.kometkoderSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.teamtiltak.teamTiltakkoderSomGirRett

/**
 * En tiltakskode som gir rett, kilden den kommer fra, og tiltakstypen vi mapper den til.
 *
 * Samme kode kan finnes hos flere kilder, så det er [kilde] og [eksternTiltakskode] sammen som identifiserer en rad.
 */
data class EksternTiltakskodeSomGirRett(
    val kilde: Tiltakskilde,
    val eksternTiltakskode: String,
    val tiltakstype: TiltakstypeSomGirRett,
)

/**
 * Samler alle tiltakskoder som gir rett, på tvers av kildene, for deling med konsumenter som datavarehuset.
 *
 * Lista settes sammen av de samme tabellene som klassifiserer deltakelsene, så kodeverket kan ikke avvike fra mappingen vi faktisk bruker.
 */
val eksterneTiltakskoderSomGirRett: List<EksternTiltakskodeSomGirRett> =
    arenakoderSomGirRett.tilTiltakskoderSomGirRett(Tiltakskilde.Arena) +
        kometkoderSomGirRett.tilTiltakskoderSomGirRett(Tiltakskilde.Komet) +
        teamTiltakkoderSomGirRett.tilTiltakskoderSomGirRett(Tiltakskilde.TeamTiltak)

private fun Map<String, TiltakstypeSomGirRett>.tilTiltakskoderSomGirRett(kilde: Tiltakskilde): List<EksternTiltakskodeSomGirRett> =
    map { (tiltakskode, tiltakstype) -> EksternTiltakskodeSomGirRett(kilde, tiltakskode, tiltakstype) }
