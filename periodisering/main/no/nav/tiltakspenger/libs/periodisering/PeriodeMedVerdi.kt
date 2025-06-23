package no.nav.tiltakspenger.libs.periodisering

import arrow.core.Nel
import arrow.core.toNonEmptyListOrThrow
import no.nav.tiltakspenger.libs.periodisering.erSammenhengende
import no.nav.tiltakspenger.libs.periodisering.perioder

/**
 * Denne klassen representerer en sammenhengende periode som har samme verdi for hele perioden.
 * Perioden kan ikke ha "hull" som ikke har en verdi.
 * Støtter ikke null.
 */
data class PeriodeMedVerdi<T : Any>(
    val verdi: T,
    val periode: Periode,
)

/** @throws NoSuchElementException Dersom listen er tom. */
fun <T : Any> List<PeriodeMedVerdi<T>>.totalPeriode(): Periode {
    return Periode(fraOgMed = minOf { it.periode.fraOgMed }, tilOgMed = maxOf { it.periode.tilOgMed })
}

fun <T : Any> List<PeriodeMedVerdi<T>>.harAllePerioderSammeVerdi(): Boolean {
    return this.map { it.verdi }.distinct().size <= 1
}

fun <T : Any> List<PeriodeMedVerdi<T>>.trekkFra(periode: Periode): List<PeriodeMedVerdi<T>> {
    return this.flatMap {
        it.periode.trekkFra(listOf(periode))
            .map { nyPeriode -> PeriodeMedVerdi(it.verdi, nyPeriode) }
    }
}

/** Sjekker at denne listen er sortert, sammenhengende og uten duplikater. */
fun List<PeriodeMedVerdi<*>>.erSammenhengende(): Boolean {
    return isEmpty() ||
        zipWithNext { a, b ->
            a.periode.tilOgMed.plusDays(1) == b.periode.fraOgMed
        }.all { it }
}

fun List<PeriodeMedVerdi<*>>.perioder(): List<Periode> = map { it.periode }

/** Krever IKKE at alle elementer i lista har samme verdi for å fungere */
fun <T : Any> List<PeriodeMedVerdi<T>>.trekkFra(periodeMedVerdi: PeriodeMedVerdi<T>): List<PeriodeMedVerdi<T>> {
    return this.trekkFra(periodeMedVerdi.periode)
}

/** Krever at alle elementer i lista har samme verdi for å fungere! */
fun <T : Any> List<PeriodeMedVerdi<T>>.slåSammenTilstøtendePerioderMedSammeVerdi(): List<PeriodeMedVerdi<T>> {
    return this.leggSammenPerioderMedSammeVerdi(false)
}

/** Krever IKKE at alle elementer i lista har samme verdi for å fungere */
fun <T : Any> Nel<PeriodeMedVerdi<T>>.slåSammenTilstøtendePerioder(): Nel<PeriodeMedVerdi<T>> {
    return this.groupBy { it.verdi }.values
        .flatMap { listeMedLikeVerdier -> listeMedLikeVerdier.slåSammenTilstøtendePerioderMedSammeVerdi() }
        .sortedBy { it.periode.fraOgMed }
        .toNonEmptyListOrThrow()
}

/** Krever IKKE at alle elementer i lista har samme verdi for å fungere */
fun <T : Any> List<PeriodeMedVerdi<T>>.slåSammenTilstøtendePerioder(): List<PeriodeMedVerdi<T>> {
    kastHvisOverlapper()
    kastHvisUsortert()
    return this.groupBy { it.verdi }.values
        .flatMap { listeMedLikeVerdier -> listeMedLikeVerdier.slåSammenTilstøtendePerioderMedSammeVerdi() }
        .sortedBy { it.periode.fraOgMed }
}

/** Krever at alle elementer i lista har samme verdi for å fungere! */
fun <T : Any> List<PeriodeMedVerdi<T>>.leggTilPeriodeMedSammeVerdi(periodeMedVerdi: PeriodeMedVerdi<T>): List<PeriodeMedVerdi<T>> {
    return (this + periodeMedVerdi).leggSammenPerioderMedSammeVerdi(false)
}

/** Krever at alle elementer i lista har samme verdi for å fungere! */
fun <T : Any> List<PeriodeMedVerdi<T>>.leggSammenPerioderMedSammeVerdi(godtaOverlapp: Boolean = true): List<PeriodeMedVerdi<T>> {
    if (!this.harAllePerioderSammeVerdi()) {
        throw IllegalArgumentException("Kan bare legge sammen perioder med samme verdi")
    }
    if (!godtaOverlapp && this.map { it.periode }.inneholderOverlapp()) {
        throw IllegalArgumentException("Listen inneholder overlappende perioder")
    }
    val verdi: T = this.firstOrNull()!!.verdi
    return this.map { it.periode }.leggSammen(true).map { PeriodeMedVerdi(verdi, it) }
}

/**
 * Overskriver eksisterende perioder.
 * Kan inneholde hull.
 * Krever at listen er sortert og ikke inneholder duplikater.
 */
fun <T : Any> List<PeriodeMedVerdi<T>>.setVerdiForDelperiode(
    verdi: T,
    delPeriode: Periode,
): Nel<PeriodeMedVerdi<T>> {
    kastHvisOverlapper()
    kastHvisUsortert()
    val nyPeriodeMedVerdi = PeriodeMedVerdi(verdi, delPeriode)
    return (this.trekkFra(nyPeriodeMedVerdi) + nyPeriodeMedVerdi).sortedBy { it.periode.fraOgMed }
        .slåSammenTilstøtendePerioder().toNonEmptyListOrThrow()
}

/**
 * Krever at totalPerioden for begge listene er like og at periodene er sammenhengende.
 */
fun <T : Any, U : Any, V : Any> List<PeriodeMedVerdi<T>>.kombiner(
    other: List<PeriodeMedVerdi<U>>,
    transform: (T, U) -> V,
): Nel<PeriodeMedVerdi<V>> {
    require(totalPeriode() == other.totalPeriode()) { "Krever at begge listene har samme periodisering." }
    kastHvisUsammenhengende()
    other.kastHvisUsammenhengende()
    return this.flatMap { thisPeriodeMedVerdi ->
        other.mapNotNull { otherPeriodeMedVerdi ->
            thisPeriodeMedVerdi.periode.overlappendePeriode(otherPeriodeMedVerdi.periode)?.let { overlappendePeriode ->
                PeriodeMedVerdi(transform(thisPeriodeMedVerdi.verdi, otherPeriodeMedVerdi.verdi), overlappendePeriode)
            }
        }
    }.sortedBy { it.periode.fraOgMed }.slåSammenTilstøtendePerioder().toNonEmptyListOrThrow()
}

/** Listen trenger ikke være sammenhengende, men krever sortert uten overlapp. */
fun <T : Any> List<PeriodeMedVerdi<T>>.harIkkeOverlappMed(periode: Periode): Boolean = !this@harIkkeOverlappMed.overlapper(periode)

/** Listen trenger ikke være sammenhengende, men krever sortert uten overlapp. */
fun <T : Any> List<PeriodeMedVerdi<T>>.overlapper(periode: Periode): Boolean {
    kastHvisOverlapper()
    kastHvisUsortert()
    return overlappendePeriode(periode).isNotEmpty()
}

/** Listen trenger ikke være sammenhengende, men krever sortert uten overlapp. */
fun List<PeriodeMedVerdi<*>>.overlapper(other: List<PeriodeMedVerdi<*>>): Boolean {
    kastHvisOverlapper()
    kastHvisUsortert()
    return this.perioder().overlappendePerioder(other.perioder()).isNotEmpty()
}

/**
 * Returnerer den overlappende perioden eller periodene hvis hull
 * Trenger ikke være sammenhengende, men krever sortert uten overlapp.
 */
fun <T : Any> List<PeriodeMedVerdi<T>>.overlappendePeriode(periode: Periode): List<PeriodeMedVerdi<T>> {
    kastHvisOverlapper()
    kastHvisUsortert()
    return this
        .filter { it.periode.overlapperMed(periode) }
        .mapNotNull { periodeMedVerdi ->
            periodeMedVerdi.periode.overlappendePeriode(periode)?.let { overlappendePeriode ->
                periodeMedVerdi.copy(periode = overlappendePeriode)
            }
        }.slåSammenTilstøtendePerioder()
}

/**
 * Trenger ikke være sammenhengende, men krever sortert uten overlapp.
 */
fun <T : Any, U : Any> List<PeriodeMedVerdi<T>>.map(transform: (T, Periode) -> U): List<PeriodeMedVerdi<U>> {
    kastHvisOverlapper()
    kastHvisUsortert()
    return this.map {
        PeriodeMedVerdi(transform(it.verdi, it.periode), it.periode)
    }.slåSammenTilstøtendePerioder()
}

/**
 * Trenger ikke være sammenhengende, men krever sortert uten overlapp.
 */
fun <T : Any, U : Any> Nel<PeriodeMedVerdi<T>>.map(transform: (T, Periode) -> U): Nel<PeriodeMedVerdi<U>> {
    kastHvisOverlapper()
    kastHvisUsortert()
    return this.map {
        PeriodeMedVerdi(transform(it.verdi, it.periode), it.periode)
    }.slåSammenTilstøtendePerioder()
}

fun List<PeriodeMedVerdi<*>>.kastHvisOverlapper() {
    require(isEmpty() || zipWithNext().none { it.first.periode.overlapperMed(it.second.periode) }) {
        "Støtter ikke overlappende perioder, men var: ${perioder()}"
    }
}

fun List<PeriodeMedVerdi<*>>.kastHvisUsortert() {
    require(isEmpty() || zipWithNext().all { it.first.periode.tilOgMed < it.second.periode.fraOgMed }) {
        "Forventet at periodene var sortert, men var: ${perioder()}"
    }
}

fun List<PeriodeMedVerdi<*>>.kastHvisUsammenhengende() {
    require(erSammenhengende()) {
        "Forventet at periodene var sammenhengende, men var: ${perioder()}"
    }
}
