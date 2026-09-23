package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Deltakelsesformene fra én henting vi ikke kjente igjen — samletypen rundt [UkjentDeltakelsesform].
 * Samme kode kan stå flere ganger: antallet sier hvor mange rader som ikke lot seg tolke.
 */
data class UkjenteDeltakelsesformer(
    val verdi: List<UkjentDeltakelsesform>,
) {
    /** Formene som del av [UkjentKildeverdi]-flaten, slik varsling leser dem sammen med resten. */
    val ukjenteKildeverdier: List<UkjentKildeverdi> get() = verdi
}
