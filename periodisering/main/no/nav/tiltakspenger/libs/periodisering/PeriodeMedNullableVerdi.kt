package no.nav.tiltakspenger.libs.periodisering

/*
Denne klassen representerer en sammenhengende periode som har samme verdi for hele perioden.
Perioden kan ikke ha "hull" som ikke har en verdi
 */
data class PeriodeMedNullableVerdi<T>(
    val verdi: T,
    val periode: Periode,
)

internal fun <T> List<PeriodeMedNullableVerdi<T>>.allePerioderHarSammeVerdi() =
    this.map { it.verdi }.distinct().size <= 1

internal fun <T> List<PeriodeMedNullableVerdi<T>>.perioderMedSammeVerdi(verdi: T): List<PeriodeMedNullableVerdi<T>> =
    this.filter { it.verdi == verdi }

internal fun <T> List<PeriodeMedNullableVerdi<T>>.perioderMedUlikVerdi(verdi: T): List<PeriodeMedNullableVerdi<T>> =
    this.filter { it.verdi != verdi }

internal fun <T> List<PeriodeMedNullableVerdi<T>>.trekkFra(periode: Periode): List<PeriodeMedNullableVerdi<T>> =
    this.flatMap {
        it.periode.trekkFra(listOf(periode))
            .map { nyPeriode -> PeriodeMedNullableVerdi(it.verdi, nyPeriode) }
    }
