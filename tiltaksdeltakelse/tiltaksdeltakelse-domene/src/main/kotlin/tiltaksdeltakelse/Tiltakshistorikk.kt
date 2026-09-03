package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDateTime

/**
 * Resultatet av én henting fra `tiltakshistorikk`: deltakelsene, kompletthet, og når vi hentet.
 *
 * [hentetTidspunkt] ligger ytterst fordi det gjelder hele svaret, og er grunnlaget for et senere utdatert-flagg sammen med kildens eget statustidspunkt.
 * Produsenten tar alltid klokken som parameter — aldri systemklokke, aldri default-verdi.
 * Kompletthet bor her og ikke på [Tiltaksdeltakelser]: samletypen kan bygges fra lagrede rader, mens de ukjente formene bare finnes i selve hentingen.
 */
data class Tiltakshistorikk(
    val deltakelser: Tiltaksdeltakelser,
    val ukjenteDeltakelsesformer: UkjenteDeltakelsesformer,
    val hentetTidspunkt: LocalDateTime,
) {
    /** Alt ved hentingen som ikke lot seg tolke — deltakelsenes ukjente verdier og ukjente deltakelsesformer — til varsling og visning. */
    val ukjenteKildeverdier: List<UkjentKildeverdi>
        get() = deltakelser.ukjenteKildeverdier + ukjenteDeltakelsesformer.ukjenteKildeverdier
}
