package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Domenets egen ordlyd for hva en deltakelse står i.
 *
 * Kildene har 36 statuser til sammen, men vi trenger bare å skille tre tilstander for å avgjøre rett til tiltakspenger.
 * Kildens egen status kastes ikke — den bevares i [Kildestatus], som er det saksbehandler skal se.
 *
 * Kategoriene er navngitt etter **fakta**, ikke etter rettigheter, og rettighetene utledes.
 * Endres politikken senere — for eksempel at en tildelt plass kan innvilges før oppstart — endres predikatet, ikke taksonomien.
 */
enum class Deltakerstatus {
    /**
     * Deltakelsen er i gang eller gjennomført.
     */
    DeltarEllerHarDeltatt,

    /**
     * Plass er tildelt, men deltakelsen har ikke startet.
     */
    TildeltIkkeStartet,

    /**
     * Ingen plass: under vurdering, venteliste, avslag, feilregistrering eller utkast.
     */
    IngenPlass,
    ;

    /**
     * Om bruker kan søke tiltakspenger for deltakelsen.
     * Speiler dagens guard i søknaden: tildelt plass holder, man trenger ikke ha startet.
     */
    val girRettTilÅSøke: Boolean get() = this != IngenPlass

    /**
     * Om deltakelsen kan innvilges.
     * Speiler dagens krav: deltakelsen må være i gang eller gjennomført.
     * Merk at innvilgelse i tillegg krever at både fra- og til-dato er kjent; det er en egenskap ved deltakelsen, ikke ved statusen.
     */
    val girRettTilInnvilgelse: Boolean get() = this == DeltarEllerHarDeltatt
}
