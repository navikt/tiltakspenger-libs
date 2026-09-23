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
     * Ingen plass: under vurdering, ikke møtt, venteliste, avslag, feilregistrering eller utkast.
     */
    IkkeDeltatt,
    ;

    /**
     * Om bruker kan søke tiltakspenger for deltakelsen.
     * Speiler dagens guard i søknaden: tildelt plass holder, man trenger ikke ha startet.
     * En feilregistrert status hos kilden kan gjøre at bruker likevel skal kunne søke; det håndteres i så fall av et unntak i `tiltakspenger-soknad-api`, aldri ved å endre mappingen fra kildestatus.
     */
    val girRettTilÅSøke: Boolean get() = this != IkkeDeltatt

    /**
     * Om kilden tilsier at deltakelsen er i gang eller gjennomført.
     * Dagens innvilgelsesguard bygger på akkurat dette, men ordet innvilgelse hører ikke hjemme her: om noe kan innvilges avgjøres i `tiltakspenger-saksbehandling-api`, som også veier saksbehandlers vurdering.
     * Merk at innvilgelse i tillegg krever at både fra- og til-dato er kjent; det er en egenskap ved deltakelsen, ikke ved statusen.
     * En feilregistrert status hos kilden kan gjøre at saksbehandler likevel må innvilge; det håndteres av en overstyring i `tiltakspenger-saksbehandling-api`, aldri ved å endre dette predikatet eller mappingen fra kildestatus.
     */
    val deltarEllerHarDeltatt: Boolean get() = this == DeltarEllerHarDeltatt
}
