package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Meldingene fra én henting — kontraktens beskjeder om svaret som helhet, pakket som samletype.
 * Tom betyr at kilden ikke meldte noe, og at svaret skal være komplett.
 */
data class Tiltakshistorikkmeldinger(
    val verdi: List<Tiltakshistorikkmelding>,
) {
    init {
        require(verdi.distinctBy { it.kodeIKontrakten }.size == verdi.size) {
            "Meldingene må ha unike koder — kontrakten sender dem som et sett"
        }
    }

    /** Kildene svaret er ufullstendig for — deltakelser derfra kan mangle. */
    val manglendeKilder: Set<Tiltakskilde> get() = verdi.mapNotNull { it.manglendeKilde }.toSet()

    /** Meldingene vi ikke kjenner igjen — en ny melding betyr sannsynligvis at svaret er ufullstendig på en ny måte. */
    val ukjenteKildeverdier: List<UkjentKildeverdi> get() = verdi.filterIsInstance<Tiltakshistorikkmelding.Ukjent>()
}
