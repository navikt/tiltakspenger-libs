package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDateTime

/**
 * Resultatet av én henting fra `tiltakshistorikk`: deltakelsene, deltakelsesformene vi ikke kjenner, og når vi hentet.
 *
 * [hentetTidspunkt] ligger ytterst fordi det gjelder hele svaret, og er grunnlaget for et senere utdatert-flagg sammen med kildens eget statustidspunkt.
 * Produsenten tar alltid klokken som parameter — aldri systemklokke, aldri default-verdi.
 * De ukjente formene bor her og ikke på [Tiltaksdeltakelser]: samletypen kan bygges fra lagrede rader, mens formene vi ikke kjenner bare finnes i selve hentingen.
 * Svaret bærer ikke noe kompletthetssignal: kontraktens `meldinger` (manglende historikk fra Team Tiltak) ble fjernet i september 2026, og var aldri et særtilfelle vi håndterte.
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
