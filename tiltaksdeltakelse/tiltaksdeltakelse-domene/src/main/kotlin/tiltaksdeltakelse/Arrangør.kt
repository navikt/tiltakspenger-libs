package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import no.nav.tiltakspenger.libs.common.Virksomhetsnavn

/**
 * Arrangøren som gjennomfører tiltaket.
 *
 * [hovedenhet] er juridisk enhet hos arrangøren, [underenhet] er enheten gjennomføringen faktisk er registrert på — begge fra Brønnøysundregistrene.
 * Begge navn kan mangle: kilden oppgir dem som valgfrie, og for eldre deltakelser er de ofte ukjente.
 * Team Tiltak oppgir kun én virksomhet (arbeidsgiveren); adapteren legger den i [underenhet] og lar [hovedenhet] stå tom, siden arbeidsgiveren er enheten personen faktisk møter hos — det underenheten betyr for de andre kildene.
 *
 * Arrangørnavn skal det aldri være domenelogikk på.
 * Det er kun til visning, for å kjenne igjen og skille tiltaksdeltakelser fra hverandre, og hvilken av de to enhetene som vises er visningens valg.
 *
 * Navnene er stedsinformasjon, og typen [Virksomhetsnavn] bærer maskeringen.
 * Denne klassen trenger derfor ingen egen `toString()`: den genererte kaller `toString()` på feltene, som allerede maskerer.
 */
data class Arrangør(
    val hovedenhet: Virksomhetsnavn?,
    val underenhet: Virksomhetsnavn?,
)
