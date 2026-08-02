package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Tiltakstypen slik den kom fra kilden, klassifisert etter om vi kjenner den igjen og om den gir rett.
 *
 * Dette er inngangsverdien til [tiltaksdeltakelse]-fabrikken.
 * Klassifiseringen gjøres i infrastrukturen, som eier kodetabellene fra Arena, Komet og Team Tiltak.
 *
 * Granulariteten er bevisst skjev.
 * [SomGirRett] bærer i tillegg en enum fordi vi diskriminerer på den — den avgjør blant annet stønadstype ved utbetaling.
 * De to andre bærer bare koden, fordi vi aldri trenger å skille dem fra hverandre.
 */
sealed interface Tiltakstype {
    /**
     * Tiltakskoden slik kilden oppga den, for eksempel Arenas `ARBFORB` eller Komets `ARBEIDSFORBEREDENDE_TRENING`.
     *
     * Bæres på alle varianter, også de som gir rett.
     * Koden er det felles språket mot veiledere, andre team og kildesystemene — den som står i Arena når noen ringer og spør.
     * Vår egen [TiltakstypeSomGirRett] er en tolkning, og duger ikke i den samtalen.
     *
     * **Kun til visning og gjenkjenning.**
     * Det skal aldri være domenelogikk på denne; da er det [SomGirRett.tiltakstype] som gjelder.
     */
    val tiltakskodeFraKilden: String

    /**
     * Koden er kjent og gir rett til tiltakspenger.
     */
    data class SomGirRett(
        override val tiltakskodeFraKilden: String,
        val tiltakstype: TiltakstypeSomGirRett,
    ) : Tiltakstype

    /**
     * Koden er kjent, men gir ikke rett til tiltakspenger.
     */
    data class SomIkkeGirRett(
        override val tiltakskodeFraKilden: String,
    ) : Tiltakstype

    /**
     * Koden er ikke i noen av tabellene våre.
     *
     * Arena oppgir tiltakskoden som fritekst, så en ukjent kode er en påregnelig hendelse og ikke en feil.
     * Den kan i prinsippet gi rett, så deltakelsen skal varsles på — men aldri brukes som grunnlag for vedtak før noen har sett på den.
     */
    data class Ukjent(
        override val tiltakskodeFraKilden: String,
    ) : Tiltakstype
}
