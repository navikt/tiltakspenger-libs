package no.nav.tiltakspenger.libs.periodisering

/*
Denne klassen representerer en sammenhengende periode som har samme verdi for hele perioden.
Perioden kan ikke ha "hull" som ikke har en verdi
Perioden har med kilde for å kunne si til frontend hvor verdien kommer fra
 */
data class PeriodeMedKildeOgVerdi<T>(
    val verdi: T,
    val periode: Periode,
    val kilde: String,
)
