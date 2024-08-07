package no.nav.tiltakspenger.libs.periodisering

/*
Denne klassen representerer en sammenhengende periode som har samme verdi for hele perioden.
Perioden kan ikke ha "hull" som ikke har en verdi
 */
data class PeriodeMedVerdi<T>(
    val verdi: T,
    val periode: Periode,
)

internal fun <T> List<PeriodeMedVerdi<T>>.harAllePerioderSammeVerdi(): Boolean =
    this.map { it.verdi }.distinct().size <= 1

internal fun <T> List<PeriodeMedVerdi<T>>.perioderMedSammeVerdi(verdi: T): List<PeriodeMedVerdi<T>> =
    this.filter { it.verdi == verdi }

internal fun <T> List<PeriodeMedVerdi<T>>.perioderMedUlikVerdi(verdi: T): List<PeriodeMedVerdi<T>> =
    this.filter { it.verdi != verdi }

internal fun <T> List<PeriodeMedVerdi<T>>.trekkFra(periode: Periode): List<PeriodeMedVerdi<T>> =
    this.flatMap {
        it.periode.trekkFra(listOf(periode))
            .map { nyPeriode -> PeriodeMedVerdi(it.verdi, nyPeriode) }
    }
