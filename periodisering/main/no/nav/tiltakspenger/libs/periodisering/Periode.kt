package no.nav.tiltakspenger.libs.periodisering

import com.google.common.collect.BoundType
import com.google.common.collect.DiscreteDomain
import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
En Periode med LocalDate.MIN og/eller LocalDate.MAX er ment å tilsvare en åpen periode
Så bruk LocalDate.MIN/MAX i stedet for null
 */
class Periode(
    fraOgMed: LocalDate,
    tilOgMed: LocalDate,
) {
    class LocalDateDiscreteDomain : DiscreteDomain<LocalDate>() {
        override fun next(value: LocalDate): LocalDate = value.plusDays(1)

        override fun previous(value: LocalDate): LocalDate = value.minusDays(1)

        override fun distance(
            start: LocalDate,
            end: LocalDate,
        ): Long = start.until(end).days.toLong()
    }

    init {
        require(!fraOgMed.isAfter(tilOgMed)) { "$fraOgMed kan ikke være etter $tilOgMed" }
    }

    companion object {
        val domain by lazy { LocalDateDiscreteDomain() }
    }

    val range: Range<LocalDate> by lazy { lagRangeAvFomOgTom(fraOgMed, tilOgMed) }

    private fun lagRangeAvFomOgTom(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
    ): Range<LocalDate> =
        when {
            fraOgMed == LocalDate.MIN && tilOgMed == LocalDate.MAX -> Range.all<LocalDate>().canonical(domain)
            fraOgMed == LocalDate.MIN && tilOgMed != LocalDate.MAX -> Range.atMost(tilOgMed).canonical(domain)
            fraOgMed != LocalDate.MIN && tilOgMed == LocalDate.MAX -> Range.atLeast(fraOgMed).canonical(domain)
            else -> Range.closed(fraOgMed, tilOgMed).canonical(domain)
        }

    val fraOgMed: LocalDate by lazy { range.fraOgMed() }
    val tilOgMed: LocalDate by lazy { range.tilOgMed() }

    /**
     * Inkluderer første og siste dag.
     * @throws IllegalArgumentException hvis [tilOgMed] er LocalDate.MAX
     */
    val antallDager: Long by lazy {
        if (fraOgMed == LocalDate.MIN || tilOgMed == LocalDate.MAX) {
            throw IllegalArgumentException("Det gir ikke mening å beregne antall dager for en periode som går fra LocalDate.MIN til LocalDate.MAX")
        }
        ChronoUnit.DAYS.between(fraOgMed, tilOgMed) + 1
    }

    fun inneholderHele(periode: Periode) = this.range.encloses(periode.range)

    fun overlapperMed(periode: Periode) =
        try {
            !this.range.intersection(periode.range).isEmpty
        } catch (iae: IllegalArgumentException) {
            false
        }

    fun overlappendePeriode(periode: Periode): Periode? =
        try {
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

    /**
     * Sjekker om til og med i LHS er dagen før fra og med i RHS
     */
    fun tilstøter(other: Periode): Boolean = this.tilOgMed.plusDays(1) == other.fraOgMed

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Periode) return false

        if (range != other.range) return false

        return true
    }

    override fun hashCode(): Int = range.hashCode()

    override fun toString(): String = "Periode(fraOgMed=$fraOgMed tilOgMed=$tilOgMed)"

    fun tilNorskFormat(): String = "${fraOgMed.format(norskDatoMedPunktumFormatter)} - ${tilOgMed.format(norskDatoMedPunktumFormatter)}"

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

    fun leggTil(annenPeriode: Periode): List<Periode> = listOf(this).leggSammenMed(annenPeriode, true)

    fun tilDager(): List<LocalDate> = fraOgMed.datesUntil(tilOgMed.plusDays(1)).toList()

    fun minusDager(dager: Long): Periode = Periode(fraOgMed.minusDays(dager), tilOgMed.minusDays(dager))
    fun minus14Dager(): Periode = minusDager(14)
    fun minusFraOgMed(dager: Long): Periode = Periode(fraOgMed.minusDays(dager), tilOgMed)
    fun minusTilOgMed(dager: Long): Periode = Periode(fraOgMed, tilOgMed.minusDays(dager))

    fun plusDager(dager: Long): Periode = Periode(fraOgMed.plusDays(dager), tilOgMed.plusDays(dager))
    fun plus14Dager(): Periode = plusDager(14)
    fun plusFraOgMed(dager: Long): Periode = Periode(fraOgMed.plusDays(dager), tilOgMed)
    fun plusTilOgMed(dager: Long): Periode = Periode(fraOgMed, tilOgMed.plusDays(dager))
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

/**
 * Sjekker at for hver til og med er dagen før neste fra og med. Brukes gjerne i sammenhenger der man periodiserer en vedtaksperiode. Dette skal tilsvare logikken i [Periodisering]
 *
 * Vil returnere false dersom listen ikke er sortert, har hull eller overlapp.
 * @return true dersom listen har mindre enn to elementer, eller alle periodene tilstøter hverandre.
 */
fun List<Periode>.tilstøter(): Boolean {
    if (this.size < 2) {
        return true
    }
    return this
        .zipWithNext()
        .all { (a, b) -> a.tilstøter(b) }
}

fun List<Periode>.leggSammen(godtaOverlapp: Boolean = true): List<Periode> {
    if (!godtaOverlapp && this.inneholderOverlapp()) {
        throw IllegalArgumentException("Listen inneholder overlappende perioder")
    }
    val rangeSet = TreeRangeSet.create<LocalDate>()
    rangeSet.addAll(this.map { it.range })
    return rangeSet.asRanges().toPerioder()
}

fun List<Periode>.leggSammenMed(
    periode: Periode,
    godtaOverlapp: Boolean = true,
): List<Periode> = (this + periode).leggSammen(godtaOverlapp)

fun List<Periode>.leggSammenMed(
    perioder: List<Periode>,
    godtaOverlapp: Boolean = true,
): List<Periode> = (this + perioder).leggSammen(godtaOverlapp)

fun List<Periode>.overlappendePerioder(other: List<Periode>): List<Periode> =
    this
        .flatMap { thisPeriode ->
            other.map { otherPeriode ->
                thisPeriode.overlappendePeriode(otherPeriode)
            }
        }.filterNotNull()
        .leggSammen()

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

fun List<Periode>.overlapper(periode: Periode): Boolean = this.any { it.overlapperMed(periode) }
fun List<Periode>.overlapperIkke(periode: Periode): Boolean = !this.any { it.overlapperMed(periode) }
