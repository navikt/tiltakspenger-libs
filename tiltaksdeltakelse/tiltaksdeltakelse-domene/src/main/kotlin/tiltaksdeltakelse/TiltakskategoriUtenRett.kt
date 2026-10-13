package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Hva slags tiltak en kjent tiltakstype uten rett til tiltakspenger er.
 *
 * Klassifiseringen gjøres i infrastrukturen, som eier kodetabellene, på samme måte som [TiltakstypeSomGirRett].
 * Dermed slipper konsumentene å gjenkjenne kildens koder selv, og [Tiltakstype.tiltakskodeFraKilden] forblir ren visning.
 */
enum class TiltakskategoriUtenRett {
    /**
     * Alle former for lønnstilskudd, fra Arena og Team Tiltak.
     * Gir ikke rett til tiltakspenger, men skal vises til saksbehandler.
     */
    Lønnstilskudd,

    /** Alle andre kjente tiltakstyper uten rett. */
    Annet,
}
