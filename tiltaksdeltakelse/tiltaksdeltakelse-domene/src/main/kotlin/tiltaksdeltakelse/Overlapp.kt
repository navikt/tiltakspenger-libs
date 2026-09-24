package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import no.nav.tiltakspenger.libs.periode.Periode

/**
 * Om en deltakelse overlapper med en periode.
 *
 * Trippelverdien er ekte: kilden mangler ofte datoer, og da kan vi hverken bekrefte eller avkrefte overlapp.
 * En `Boolean?` ville skjult hva `null` betyr — [Kanskje] sier det rett ut.
 */
enum class Overlapp {
    Ja,
    Nei,
    Kanskje,
}

/**
 * Om deltakelsen overlapper med [periode], så langt kildedataene rekker.
 *
 * Har kilden begge datoene, er svaret [Overlapp.Ja] eller [Overlapp.Nei].
 * Har den bare én, kan vi fortsatt avkrefte — en deltakelse som starter etter perioden overlapper ikke — men aldri bekrefte.
 * En [Tiltaksdeltakelse.Ugyldig] svarer alltid [Overlapp.Kanskje]: datoene henger ikke sammen, og da vet vi ingenting.
 */
fun Tiltaksdeltakelse.overlapper(periode: Periode): Overlapp {
    if (this is Tiltaksdeltakelse.Ugyldig) {
        return Overlapp.Kanskje
    }

    val fraKilden = periodeFraKilden
    if (fraKilden != null) {
        return if (fraKilden.overlapperMed(periode)) Overlapp.Ja else Overlapp.Nei
    }

    val fom = fraOgMed
    val tom = tilOgMed
    return when {
        fom != null && fom.isAfter(periode.tilOgMed) -> Overlapp.Nei
        tom != null && tom.isBefore(periode.fraOgMed) -> Overlapp.Nei
        else -> Overlapp.Kanskje
    }
}
