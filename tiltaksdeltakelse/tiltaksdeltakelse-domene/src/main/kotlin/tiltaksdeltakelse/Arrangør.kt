package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Arrangøren som gjennomfører tiltaket.
 *
 * [hovedenhet] er juridisk enhet hos arrangøren, [underenhet] er enheten gjennomføringen faktisk er registrert på — begge fra Brønnøysundregistrene.
 * Begge navn kan mangle: kilden oppgir dem som valgfrie, og for eldre deltakelser er de ofte ukjente.
 * Team Tiltak oppgir kun én virksomhet (arbeidsgiveren).
 *
 * Typen tar bevisst ikke stilling til hvilken av de to som skal vises.
 * Dagens løsning velger hovedenhet før underenhet, men det er trolig feil for visning siden underenheten er der tiltaket kjøres.
 * Valget hører hjemme der navnet konsumeres, og avgjøres på tallene fra skyggekjøringen.
 */
data class Arrangør(
    val hovedenhet: String?,
    val underenhet: String?,
) {
    /**
     * Arrangørnavn er stedsinformasjon: det røper hvor personen faktisk møter opp, ofte helt ned på gateadresse («Arrangør AS avd Strandveien»).
     * For personer med kode 6, kode 7 eller skjerming er det nettopp den opplysningen som ikke skal spres.
     * Derfor maskeres navnene her, slik at en `$deltakelse` i vanlig logg ikke lekker dem.
     *
     * Verdiene er fortsatt tilgjengelige gjennom feltene.
     * Sikkerlogg og visning må hente dem eksplisitt — samme disiplin som [no.nav.tiltakspenger.libs.common.Fnr].
     *
     * Om navnet er kjent eller ikke er ikke stedsinformasjon, og skilles derfor fortsatt fra maskert verdi.
     */
    override fun toString(): String =
        "Arrangør(hovedenhet=${hovedenhet?.let { "*****" }}, underenhet=${underenhet?.let { "*****" }})"
}
