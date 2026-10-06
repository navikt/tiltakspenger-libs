package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import no.nav.tiltakspenger.libs.periode.Overlapp
import no.nav.tiltakspenger.libs.periode.Periode

/**
 * Om deltakelsen overlapper med [periode], så langt kildedataene rekker.
 *
 * Svaret er det samme som [no.nav.tiltakspenger.libs.periode.ÅpenPeriode.overlapperMed] gir for datoene kilden oppga.
 * Har kilden bare én dato, bekrefter den overlapp når den ligger i [periode], og avkrefter når den ligger på feil side.
 * En [Tiltaksdeltakelse.Ugyldig] svarer alltid [Overlapp.Kanskje]: datoene henger ikke sammen, og da vet vi ingenting.
 */
fun Tiltaksdeltakelse.overlapper(periode: Periode): Overlapp {
    val åpenPeriode = åpenPeriode ?: return Overlapp.Kanskje
    return åpenPeriode.overlapperMed(periode)
}
