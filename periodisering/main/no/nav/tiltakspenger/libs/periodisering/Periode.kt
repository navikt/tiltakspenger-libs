package no.nav.tiltakspenger.libs.periodisering

import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

/*
En Periode med LocalDate.MIN og/eller LocalDate.MAX er ment å tilsvare en åpen periode
Så bruk LocalDate.MIN/MAX i stedet for null
 */
data class Periode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
) : ClosedRange<LocalDate> {

    init {
        require(!fraOgMed.isAfter(tilOgMed)) { "$fraOgMed kan ikke være etter $tilOgMed" }
        require(fraOgMed != LocalDate.MAX) {
            "fraOgMed kan ikke være LocalDate.MAX. Bruk LocalDate.MIN for å representere en åpen periode"
        }
        require(tilOgMed != LocalDate.MIN) {
            "tilOgMed kan ikke være LocalDate.MIN. Bruk LocalDate.MAX for å representere en åpen periode"
        }
    }

    // ClosedRange implementation
    override val start: LocalDate = fraOgMed
    override val endInclusive: LocalDate = tilOgMed

    /**
     * Inkluderer første og siste dag.
     * @throws IllegalArgumentException hvis [fraOgMed] eller ][tilOgMed] er LocalDate.MAX
     */
    val antallDager: Long by lazy {
        if (fraOgMed == LocalDate.MIN || tilOgMed == LocalDate.MAX) {
            throw IllegalArgumentException("Det gir ikke mening å beregne antall dager for en periode som går fra LocalDate.MIN til LocalDate.MAX")
        }
        ChronoUnit.DAYS.between(fraOgMed, tilOgMed) + 1
    }

    /** Sjekker om denne perioden starter etter [dato] */
    infix fun starterEtter(dato: LocalDate): Boolean = fraOgMed.isAfter(dato)

    /** Sjekker om denne perioden starter etter [other] */
    infix fun starterEtter(other: Periode): Boolean = fraOgMed.isAfter(other.fraOgMed)

    /** Sjekker om denne perioden starter samtidig eller tidligere [other] */
    infix fun starterSamtidigEllerTidligere(other: Periode): Boolean = starterSamtidig(other) || starterTidligere(other)

    /** Sjekker om denne perioden starter samtidig eller senere enn [other] */
    infix fun starterSamtidigEllerSenere(other: Periode): Boolean = starterSamtidig(other) || starterEtter(other)

    /** Sjekker om denne perioden starter samtidig som [other] */
    infix fun starterSamtidig(other: Periode): Boolean = fraOgMed.isEqual(other.fraOgMed)

    /** Sjekker om denne perioden starter tidligere [other] */
    infix fun starterTidligere(other: Periode): Boolean = fraOgMed.isBefore(other.fraOgMed)

    /** Sjekker om denne perioden slutter samtidig eller tidligere enn [other] */
    infix fun slutterSamtidigEllerTidligere(other: Periode): Boolean = slutterSamtidig(other) || slutterTidligere(other)

    /** Sjekker om denne perioden slutter samtidig eller senere enn [other] */
    infix fun slutterSamtidigEllerSenere(other: Periode): Boolean = slutterSamtidig(other) || slutterEtter(other)

    /** Sjekker om denne perioden slutter samtidig som [other] */
    infix fun slutterSamtidig(other: Periode): Boolean = tilOgMed.isEqual(other.tilOgMed)

    /** Sjekker om denne perioden slutter tidligere enn [other] */
    infix fun slutterTidligere(other: Periode): Boolean = tilOgMed.isBefore(other.tilOgMed)

    /** Sjekker om denne perioden slutter etter [other] */
    infix fun slutterEtter(other: Periode): Boolean = tilOgMed.isAfter(other.tilOgMed)

    /** Sjekker om denne perioden slutter etter [dato] */
    infix fun slutterEtter(dato: LocalDate): Boolean = tilOgMed.isAfter(dato)

    /** Sjekker om denne perioden slutter før [other] starter */
    infix fun erFør(other: Periode): Boolean = tilOgMed.isBefore(other.fraOgMed)

    /** Sjekker om denne perioden starter etter [other] slutter */
    infix fun erEtter(other: Periode): Boolean = fraOgMed.isAfter(other.tilOgMed)

    /** Sjekker om denne perioden inneholder hele [other]. */
    fun inneholderHele(other: Periode): Boolean {
        return other.fraOgMed in this && other.tilOgMed in this
    }

    /** @return true dersom minst en dag overlapper [other] */
    fun overlapperMed(other: Periode): Boolean {
        return other.fraOgMed in this || other.tilOgMed in this || this.fraOgMed in other || this.tilOgMed in other
    }

    /** @return den overlappende perioden eller null dersom de ikke overlapper */
    fun overlappendePeriode(periode: Periode): Periode? {
        if (this.overlapperMed(periode)) {
            return Periode(
                fraOgMed = maxOf(this.fraOgMed, periode.fraOgMed),
                tilOgMed = minOf(this.tilOgMed, periode.tilOgMed),
            )
        }
        return null
    }

    /** @return den overlappende perioden eller null dersom de ikke overlapper */
    fun overlappendePerioder(perioder: List<Periode>, godtaOverlapp: Boolean = true): List<Periode> {
        if (perioder.isEmpty()) return emptyList()
        if (!godtaOverlapp && perioder.inneholderOverlapp()) {
            throw IllegalArgumentException("Listen inneholder overlappende perioder")
        }
        return perioder.mapNotNull { this.overlappendePeriode(it) }.leggSammen(godtaOverlapp)
    }

    /** Sjekker om til og med i LHS er dagen før fra og med i RHS */
    fun tilstøter(other: Periode): Boolean = this.tilOgMed.plusDays(1) == other.fraOgMed

    fun leggSammen(other: Periode, godtaOverlapp: Boolean = true): List<Periode> {
        if (!godtaOverlapp && this.overlapperMed(other)) {
            throw IllegalArgumentException("Listen inneholder overlappende perioder")
        }
        return if (this.overlapperMed(other) || this.tilstøter(other)) {
            listOf(Periode(minOf(fraOgMed, other.fraOgMed), maxOf(tilOgMed, other.tilOgMed)))
        } else {
            listOf(this, other)
        }
    }

    fun ikkeOverlappendePeriode(other: Periode): List<Periode> {
        if (this.overlapperMed(other)) {
            val overlappendePeriode = this.overlappendePeriode(other)!!

            if (overlappendePeriode == this) return emptyList()

            if (overlappendePeriode.fraOgMed > this.fraOgMed && overlappendePeriode.tilOgMed < this.tilOgMed) {
                // Den overlappende perioden ligger inni denne perioden, så vi vil ende opp med to perioder
                return listOf(
                    Periode(fraOgMed, overlappendePeriode.fraOgMed.minusDays(1)),
                    Periode(overlappendePeriode.tilOgMed.plusDays(1), tilOgMed),
                )
            }
            if (overlappendePeriode.fraOgMed > this.fraOgMed) {
                // Den overlappende perioden ligger til høyre for denne perioden, så vi vil ende opp med en periode
                return listOf(Periode(fraOgMed, overlappendePeriode.fraOgMed.minusDays(1)))
            }
            if (overlappendePeriode.tilOgMed < this.tilOgMed) {
                // Den overlappende perioden ligger til venstre for denne perioden, så vi vil ende opp med en periode
                return listOf(Periode(overlappendePeriode.tilOgMed.plusDays(1), tilOgMed))
            }
            throw IllegalStateException("Klarte ikke finne den overlappende perioden")
        }
        return listOf(this)
    }

    fun ikkeOverlappendePerioder(andrePerioder: List<Periode>, godtaOverlapp: Boolean = true): List<Periode> {
        if (andrePerioder.isEmpty()) return listOf(this)

        if (!godtaOverlapp && andrePerioder.inneholderOverlapp()) {
            throw IllegalArgumentException("Listen inneholder overlappende perioder")
        }
        return andrePerioder.leggSammen(godtaOverlapp).fold(listOf(this)) { acc, periode ->
            acc.flatMap { it.trekkFra(periode) }
        }.leggSammen(godtaOverlapp)
    }

    fun trekkFra(other: Periode): List<Periode> {
        return ikkeOverlappendePeriode(other)
    }

    fun trekkFra(andrePerioder: List<Periode>, godtaOverlapp: Boolean = true): List<Periode> {
        return ikkeOverlappendePerioder(andrePerioder, godtaOverlapp)
    }

    override fun toString(): String {
        if (fraOgMed == tilOgMed) {
            return fraOgMed.format(norskDatoFormatter)
        }
        if (fraOgMed.year == tilOgMed.year && fraOgMed.month == tilOgMed.month) {
            return "${fraOgMed.dayOfMonth}.–${tilOgMed.dayOfMonth}. ${fraOgMed.printMonth()} ${fraOgMed.year}"
        }
        if (fraOgMed.year == tilOgMed.year) {
            return "${fraOgMed.dayOfMonth}. ${fraOgMed.printMonth()} – ${tilOgMed.dayOfMonth}. ${tilOgMed.printMonth()} ${fraOgMed.year}"
        }
        if (fraOgMed == LocalDate.MIN && tilOgMed == LocalDate.MAX) {
            return "LocalDate.MIN – LocalDate.MAX"
        }
        if (fraOgMed == LocalDate.MIN) {
            return "LocalDate.MIN – ${tilOgMed.dayOfMonth}. ${tilOgMed.printMonth()} ${tilOgMed.year}"
        }
        if (tilOgMed == LocalDate.MAX) {
            return "${fraOgMed.dayOfMonth}. ${fraOgMed.printMonth()} ${fraOgMed.year} – LocalDate.MAX"
        }
        return "${fraOgMed.format(norskDatoFormatter)} – ${tilOgMed.format(norskDatoFormatter)}"
    }

    private fun LocalDate.printMonth(): String {
        return this.month.getDisplayName(TextStyle.FULL, localeNorsk).lowercase()
    }

    fun tilNorskFormat(): String =
        "${fraOgMed.format(norskDatoMedPunktumFormatter)} - ${tilOgMed.format(norskDatoMedPunktumFormatter)}"

    fun inneholder(dato: LocalDate): Boolean = dato in this

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

/** @return true dersom 2 eller flere av periodene overlapper*/
fun List<Periode>.inneholderOverlapp(): Boolean {
    if (this.size < 2) return false
    return this.sortedBy { it.fraOgMed }.zipWithNext().any { (a, b) -> a.overlapperMed(b) }
}

/**
 * Sjekker at for hver til og med er dagen før neste fra og med. Brukes gjerne i sammenhenger der man periodiserer en vedtaksperiode. Dette skal tilsvare logikken i [Periodisering]
 *
 * Vil returnere false dersom listen ikke er sortert, har hull eller overlapp.
 * @return true dersom listen har mindre enn to elementer, eller alle periodene tilstøter hverandre.
 */
fun List<Periode>.tilstøter(): Boolean {
    if (this.size < 2) return true
    return this
        .zipWithNext()
        .all { (a, b) -> a.tilstøter(b) }
}

fun List<Periode>.leggSammen(godtaOverlapp: Boolean = true): List<Periode> {
    if (!godtaOverlapp && this.inneholderOverlapp()) {
        throw IllegalArgumentException("Listen inneholder overlappende perioder")
    }
    if (this.size < 2) return this
    return this
        .sortedWith(compareBy(Periode::fraOgMed, Periode::tilOgMed))
        .fold(mutableListOf()) { acc, periode ->
            if (acc.isEmpty()) {
                acc.add(periode)
            } else {
                val last = acc.last()
                if (last.overlapperMed(periode) || last.tilstøter(periode)) {
                    acc[acc.size - 1] = Periode(
                        fraOgMed = minOf(last.fraOgMed, periode.fraOgMed),
                        tilOgMed = maxOf(last.tilOgMed, periode.tilOgMed),
                    )
                } else {
                    acc.add(periode)
                }
            }
            acc
        }
}

fun List<Periode>.leggSammenMed(
    periode: Periode,
    godtaOverlapp: Boolean = true,
): List<Periode> = (this + periode).leggSammen(godtaOverlapp)

fun List<Periode>.leggSammenMed(
    perioder: List<Periode>,
    godtaOverlapp: Boolean = true,
): List<Periode> = (this + perioder).leggSammen(godtaOverlapp)

fun List<Periode>.overlappendePerioder(other: List<Periode>): List<Periode> {
    if (this.isEmpty() || other.isEmpty()) return emptyList()
    return this.flatMap { periode1 ->
        other.mapNotNull { periode2 ->
            periode1.overlappendePeriode(periode2)
        }
    }.leggSammen()
}

fun List<Periode>.trekkFra(perioder: List<Periode>, godtaOverlapp: Boolean = true): List<Periode> {
    if (this.isEmpty() || perioder.isEmpty()) return this

    if (!godtaOverlapp && (this.inneholderOverlapp() || perioder.inneholderOverlapp())) {
        throw IllegalArgumentException("En eller begge listene inneholder overlappende perioder")
    }
    val trekkFraSlåttSammen = perioder.leggSammen(godtaOverlapp)

    return this.leggSammen().flatMap { periode ->
        periode.trekkFra(trekkFraSlåttSammen, true)
    }
}

fun List<Periode>.overlapper(periode: Periode): Boolean = this.any { it.overlapperMed(periode) }
fun List<Periode>.overlapperIkke(periode: Periode): Boolean = !this.any { it.overlapperMed(periode) }

infix fun LocalDate.til(other: LocalDate): Periode = Periode(this, other)
infix fun Int.til(other: LocalDate): Periode = Periode(other.withDayOfMonth(this), other)
