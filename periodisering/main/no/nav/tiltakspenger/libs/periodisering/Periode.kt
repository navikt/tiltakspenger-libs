package no.nav.tiltakspenger.libs.periodisering

import com.google.common.collect.BoundType
import com.google.common.collect.DiscreteDomain
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import java.time.LocalDate

/*
En Periode med LocalDate.MIN og/eller LocalDate.MAX er ment å tilsvare en åpen periode
Så bruk LocalDate.MIN/MAX i stedet for null
 */
class Periode(fraOgMed: LocalDate, tilOgMed: LocalDate) {

    class LocalDateDiscreteDomain : DiscreteDomain<LocalDate>() {
        override fun next(value: LocalDate): LocalDate {
            return value.plusDays(1)
        }

        override fun previous(value: LocalDate): LocalDate {
            return value.minusDays(1)
        }

        override fun distance(start: LocalDate, end: LocalDate): Long {
            return start.until(end).days.toLong()
        }
    }

    init {
        require(!fraOgMed.isAfter(tilOgMed)) { "$fraOgMed kan ikke være etter $tilOgMed" }
    }

    companion object {
        val domain = LocalDateDiscreteDomain()
    }

    val range: Range<LocalDate> = lagRangeAvFomOgTom(fraOgMed, tilOgMed)

    private fun lagRangeAvFomOgTom(fraOgMed: LocalDate, tilOgMed: LocalDate): Range<LocalDate> =
        when {
            fraOgMed == LocalDate.MIN && tilOgMed == LocalDate.MAX -> Range.all<LocalDate>().canonical(domain)
            fraOgMed == LocalDate.MIN && tilOgMed != LocalDate.MAX -> Range.atMost(tilOgMed).canonical(domain)
            fraOgMed != LocalDate.MIN && tilOgMed == LocalDate.MAX -> Range.atLeast(fraOgMed).canonical(domain)
            else -> Range.closed(fraOgMed, tilOgMed).canonical(domain)
        }

    val fraOgMed: LocalDate
        get() = range.fraOgMed()
    val tilOgMed: LocalDate
        get() = range.tilOgMed()

    fun kompletter(perioder: List<Periode>): List<Periode> {
        val overlappendePerioder = perioder
            .filter { periode -> this.overlapperMed(periode) }
            .sortedBy { periode -> periode.fraOgMed }

        if (overlappendePerioder.inneholderOverlapp()) {
            throw IllegalArgumentException("Periodene kan ikke inneholde overlapp")
        }
        if (overlappendePerioder.any { !inneholderHele(it) }) {
            throw IllegalArgumentException("Alle periodene må være innenfor hoved-perioden")
        }

        val mangelPerioder = this.trekkFra(perioder)
        return (mangelPerioder + perioder).sortedBy { it.fraOgMed }
    }

    fun mergeInnIPerioder(perioder: List<Periode>): List<Periode> {
        if (perioder.inneholderOverlapp()) {
            throw IllegalArgumentException("Kan ikke merge inn periode hvis periodene som er der fra før overlapper med hverandre")
        }

        val nyePerioder = mutableListOf<Periode>()
        perioder.map { periode ->
            if (!this.overlapperMed(periode)) {
                nyePerioder.add(periode)
            }
        }

        fun leggTilPeriodeMedForkortetTildato(periode: Periode) {
            val nyTildato = this.fraOgMed.minusDays(1)
            val forkortetPeriode = Periode(fraOgMed = periode.fraOgMed, tilOgMed = nyTildato)
            nyePerioder.add(forkortetPeriode)
        }

        fun leggTilPeriodeMedForskjøvetFradato(periode: Periode) {
            val nyFradato = this.tilOgMed.plusDays(1)
            val periodeMedForskjøvetFradato = Periode(fraOgMed = nyFradato, tilOgMed = periode.tilOgMed)
            nyePerioder.add(periodeMedForskjøvetFradato)
        }

        val periodeSomDekkerHele = perioder.find { periode -> periode.inneholderHele(this) }
        if (periodeSomDekkerHele != null) {
            if (periodeSomDekkerHele.fraOgMed.isBefore(this.fraOgMed)) {
                leggTilPeriodeMedForkortetTildato(periodeSomDekkerHele)
            }
            if (periodeSomDekkerHele.tilOgMed.isAfter(this.tilOgMed)) {
                leggTilPeriodeMedForskjøvetFradato(periodeSomDekkerHele)
            }
        } else {
            val perioderSomOverlapperDelvis = perioder
                .filter { (this.overlapperMed(it) && !this.inneholderHele(it)) }
                .sortedBy { it.fraOgMed }

            perioderSomOverlapperDelvis.forEach { periodeMedDelvisOverlapp ->
                if (periodeMedDelvisOverlapp.fraOgMed.isBefore(this.fraOgMed)) {
                    leggTilPeriodeMedForkortetTildato(periodeMedDelvisOverlapp)
                } else {
                    leggTilPeriodeMedForskjøvetFradato(periodeMedDelvisOverlapp)
                }
            }
        }

        nyePerioder.add(this)
        return nyePerioder.sortedBy { it.fraOgMed }
    }

    fun inneholderHele(periode: Periode) = this.range.encloses(periode.range)

    fun overlapperMed(periode: Periode) = try {
        !this.range.intersection(periode.range).isEmpty
    } catch (iae: IllegalArgumentException) {
        false
    }

    fun overlappendePeriode(periode: Periode): Periode? = try {
        this.range.intersection(periode.range).toPeriode()
    } catch (e: Exception) {
        null
    }

    fun overlappendePerioder(perioder: List<Periode>): List<Periode> {
        val rangeSet: RangeSet<LocalDate> = TreeRangeSet.create()
        perioder.forEach { periode -> this.overlappendePeriode(periode)?.range?.let { rangeSet.add(it) } }
        return rangeSet.asRanges().toPerioder()
    }

    fun ikkeOverlappendePeriode(periode: Periode): List<Periode> {
        val rangeSet: RangeSet<LocalDate> = TreeRangeSet.create()
        rangeSet.add(this.range)
        rangeSet.remove(periode.range)
        return rangeSet.asRanges().toPerioder()
    }

    fun ikkeOverlappendePerioder(perioder: List<Periode>): List<Periode> {
        val rangeSet: RangeSet<LocalDate> = TreeRangeSet.create()
        rangeSet.add(this.range)
        perioder.forEach { periode -> rangeSet.remove(periode.range) }
        return rangeSet.asRanges().toPerioder()
    }

    fun tilstøter(other: Periode): Boolean {
        return this.range.isConnected(other.range)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Periode) return false

        if (range != other.range) return false

        return true
    }

    override fun hashCode(): Int {
        return range.hashCode()
    }

    override fun toString(): String {
        return "Periode(fraOgMed=$fraOgMed tilOgMed=$tilOgMed)"
    }

    fun inneholder(dato: LocalDate): Boolean = range.contains(dato)

    fun etter(dato: LocalDate): Boolean = this.fraOgMed.isAfter(dato)

    fun før(dato: LocalDate): Boolean = this.tilOgMed.isBefore(dato)

    fun trekkFra(andrePerioder: List<Periode>): List<Periode> {
        val opprinneligeRangeSet =
            ImmutableRangeSet.Builder<LocalDate>().add(this.range).build()
        val andrePeriodeRangeSet =
            ImmutableRangeSet.Builder<LocalDate>().addAll(andrePerioder.map { it.range }).build()
        val ranges = opprinneligeRangeSet.difference(andrePeriodeRangeSet).asRanges()
        return ranges.filter { !it.canonical(domain).isEmpty }.map { it.toPeriode() }
    }

    fun leggTil(annenPeriode: Periode): List<Periode> =
        listOf(this).leggSammenMed(annenPeriode, true)

    fun tilDager(): List<LocalDate> {
        return fraOgMed.datesUntil(tilOgMed.plusDays(1)).toList()
    }
}

fun List<Periode>.inneholderOverlapp(): Boolean {
    val rangeSet = TreeRangeSet.create<LocalDate>()
    this.forEach {
        if (rangeSet.intersects(it.range)) {
            return true
        } else {
            rangeSet.add(it.range)
        }
    }
    return false
}

fun List<Periode>.inneholderOverlappEllerTilstøter(): Boolean {
    return this.inneholderOverlapp() || this.tilstøter()
}

/**
 * @return true dersom to eller flere perioder tilstøter
 */
fun List<Periode>.tilstøter(): Boolean {
    return this
        .sortedWith(compareBy<Periode> { it.fraOgMed }.thenBy { it.tilOgMed })
        .zipWithNext()
        .any { (a, b) -> a.tilstøter(b) }
}

fun List<Periode>.leggSammen(godtaOverlapp: Boolean = true): List<Periode> {
    if (!godtaOverlapp && this.inneholderOverlapp()) {
        throw IllegalArgumentException("Listen inneholder overlappende perioder")
    }
    val rangeSet = TreeRangeSet.create<LocalDate>()
    rangeSet.addAll(this.map { it.range })
    return rangeSet.asRanges().toPerioder()
}

fun List<Periode>.leggSammenMed(periode: Periode, godtaOverlapp: Boolean = true): List<Periode> {
    return (this + periode).leggSammen(godtaOverlapp)
}

fun List<Periode>.leggSammenMed(perioder: List<Periode>, godtaOverlapp: Boolean = true): List<Periode> {
    return (this + perioder).leggSammen(godtaOverlapp)
}

fun List<Periode>.overlappendePerioder(other: List<Periode>): List<Periode> {
    return this.flatMap { thisPeriode ->
        other.map { otherPeriode ->
            thisPeriode.overlappendePeriode(otherPeriode)
        }
    }
        .filterNotNull()
        .leggSammen()
}

fun List<Periode>.trekkFra(perioder: List<Periode>): List<Periode> {
    val rangeSet = TreeRangeSet.create<LocalDate>()
    rangeSet.addAll(this.map { it.range })
    rangeSet.removeAll(perioder.map { it.range })
    return rangeSet.asRanges().toPerioder()
}

fun Set<Range<LocalDate>>.toPerioder() = this.map { it.toPeriode() }
fun Range<LocalDate>.toPeriode(): Periode = Periode(this.fraOgMed(), this.tilOgMed())
fun Range<LocalDate>.fraOgMed(): LocalDate =
    if (this.hasLowerBound()) {
        if (this.lowerBoundType() == BoundType.CLOSED) this.lowerEndpoint() else this.lowerEndpoint().plusDays(1)
    } else {
        LocalDate.MIN
    }

fun Range<LocalDate>.tilOgMed(): LocalDate =
    if (this.hasUpperBound()) {
        if (this.upperBoundType() == BoundType.CLOSED) this.upperEndpoint() else this.upperEndpoint().minusDays(1)
    } else {
        LocalDate.MAX
    }
