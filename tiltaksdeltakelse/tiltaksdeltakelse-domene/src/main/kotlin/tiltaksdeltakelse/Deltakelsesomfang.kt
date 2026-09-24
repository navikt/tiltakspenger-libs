package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Hvor mye deltakeren deltar.
 *
 * [deltakelsesprosent] og [dagerPerUke] gjelder deltakelsen, [deltidsprosentPåGjennomføring] er definert på gjennomføringen.
 * Kilden dokumenterer at deltidsprosenten på gjennomføringen gjelder alle deltakelser på tiltaket, med mindre deltakelsen har sin egen.
 * Vi utleder ikke den regelen her ennå — ingen konsument bruker den i dag, og udekket kode uten kallsted er verre enn ingen kode.
 *
 * Alle tre er nullable: kildene fyller dem ut ujevnt, og særlig for eldre Arena-deltakelser mangler de.
 * Vi validerer bevisst ikke intervall (for eksempel 0–100).
 * Datakvaliteten i kildene er ujevn, og en `require` her ville tatt ned oppslaget for en verdi vi uansett bare viser videre.
 */
data class Deltakelsesomfang(
    val deltakelsesprosent: Float?,
    val dagerPerUke: Float?,
    val deltidsprosentPåGjennomføring: Float?,
)
