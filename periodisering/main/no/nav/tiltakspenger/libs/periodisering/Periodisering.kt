package no.nav.tiltakspenger.libs.periodisering

import java.time.LocalDate

/**
 * Denne klassen representerer en sammenhengende periode som kan ha ulike verdier for ulike deler av perioden.
 * Perioden kan ikke ha "hull" som ikke har en verdi og periodene kan ikke overlappe.
 * Periodene må være sortert.
 */
@Suppress("unused")
data class Periodisering<T>(

    val perioderMedVerdi: List<PeriodeMedVerdi<T>>,
) : List<PeriodeMedVerdi<T>> by perioderMedVerdi {
    constructor(vararg periodeMedVerdi: PeriodeMedVerdi<T>) : this(periodeMedVerdi.toList())

    constructor(
        initiellVerdi: T,
        totalePeriode: Periode,
    ) : this(PeriodeMedVerdi(initiellVerdi, totalePeriode))

    val perioder: List<Periode> by lazy { perioderMedVerdi.map { it.periode } }

    init {
        require(
            zipWithNext()
                .all { it.second.periode.fraOgMed == it.first.periode.tilOgMed.plusDays(1) },
        ) { "Ugyldig periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte dagen etter periode n slutter. Perioder: ${this.perioder}" }
    }

    val totalePeriode by lazy {
        Periode(
            minOf { it.periode.fraOgMed },
            maxOf { it.periode.tilOgMed },
        )
    }

    companion object {
        fun <T> empty(): Periodisering<T> {
            return Periodisering(emptyList<PeriodeMedVerdi<T>>())
        }

        fun <T> List<Periodisering<T>>.reduser(sammensattVerdi: (T, T) -> T): Periodisering<T> {
            require(this.isNotEmpty()) {
                "Ulovlig operasjon, listen med periodiseringer kan ikke være tom"
            }

            return this.reduce { total: Periodisering<T>, next: Periodisering<T> ->
                total.kombiner(next, sammensattVerdi).slåSammenTilstøtendePerioder()
            }
        }

        operator fun <T : Periodiserbar> invoke(
            vararg periode: T,
        ) = Periodisering(periode.map { PeriodeMedVerdi(it, it.periode) })

        operator fun <T : Periodiserbar> invoke(
            perioder: List<T>,
        ) = Periodisering(perioder.map { PeriodeMedVerdi(it, it.periode) })
    }

    // Offentlig API:

    fun slåSammenTilstøtendePerioder(): Periodisering<T> =
        this.copy(perioderMedVerdi = perioderMedVerdi.slåSammenTilstøtendePerioder())

    fun setVerdiForDelPeriode(
        verdi: T,
        delPeriode: Periode,
    ): Periodisering<T> = setPeriodeMedVerdi(PeriodeMedVerdi(verdi, delPeriode))

    fun <U, V> kombiner(
        other: Periodisering<U>,
        sammensattVerdi: (T, U) -> V,
    ): Periodisering<V> {
        if (totalePeriode != other.totalePeriode) {
            throw IllegalArgumentException("Perioder som skal kombineres må være like")
        }

        return this.perioderMedVerdi.flatMap { thisPeriodeMedVerdi ->
            other.mapNotNull { otherPeriodeMedVerdi ->
                thisPeriodeMedVerdi.periode.overlappendePeriode(otherPeriodeMedVerdi.periode)?.let {
                    PeriodeMedVerdi(sammensattVerdi(thisPeriodeMedVerdi.verdi, otherPeriodeMedVerdi.verdi), it)
                }
            }
        }.let {
            Periodisering(it.sortedBy { it.periode.fraOgMed })
        }
    }

    fun <U> map(kombinertVerdi: (T) -> U): Periodisering<U> {
        return this.perioderMedVerdi
            .map { PeriodeMedVerdi(kombinertVerdi(it.verdi), it.periode) }
            .slåSammenTilstøtendePerioder()
            .let { Periodisering(it) }
    }

    fun <U> flatMap(transform: (PeriodeMedVerdi<T>) -> List<PeriodeMedVerdi<U>>): Periodisering<U> {
        return this.perioderMedVerdi
            .flatMap { transform(it) }
            .slåSammenTilstøtendePerioder()
            .let { Periodisering(it) }
    }

    fun <U> flatMapPeriodisering(transform: (PeriodeMedVerdi<T>) -> Periodisering<U>): Periodisering<U> {
        return this.perioderMedVerdi
            .flatMap { transform(it).krymp(it.periode) }
            .slåSammenTilstøtendePerioder()
            .let { Periodisering(it) }
    }

    // Private hjelpemetoder:

    private fun setPeriodeMedVerdi(delPeriodeMedVerdi: PeriodeMedVerdi<T>): Periodisering<T> {
        if (!totalePeriode.inneholderHele(delPeriodeMedVerdi.periode)) {
            throw IllegalArgumentException("Angitt periode ${delPeriodeMedVerdi.periode} er ikke innenfor $totalePeriode")
        }
        val nyePerioderMedSammeVerdi =
            perioderMedVerdi
                .perioderMedSammeVerdi(delPeriodeMedVerdi.verdi)
                .trekkFra(delPeriodeMedVerdi)
                .leggTilPeriodeMedSammeVerdi(delPeriodeMedVerdi)
        val nyePerioderMedUlikVerdi =
            perioderMedVerdi
                .perioderMedUlikVerdi(delPeriodeMedVerdi.verdi)
                .trekkFra(delPeriodeMedVerdi)
        return Periodisering((nyePerioderMedSammeVerdi + nyePerioderMedUlikVerdi).sortedBy { it.periode.fraOgMed })
    }

    // Krever IKKE at alle elementer i lista har samme verdi for å fungere
    private fun <T> List<PeriodeMedVerdi<T>>.trekkFra(periodeMedVerdi: PeriodeMedVerdi<T>): List<PeriodeMedVerdi<T>> =
        this.trekkFra(periodeMedVerdi.periode)

    // Krever at alle elementer i lista har samme verdi for å fungere!
    private fun <T> List<PeriodeMedVerdi<T>>.leggSammenPerioderMedSammeVerdi(godtaOverlapp: Boolean = true): List<PeriodeMedVerdi<T>> {
        if (!this.harAllePerioderSammeVerdi()) {
            throw IllegalArgumentException("Kan bare legge sammen perioder med samme verdi")
        }
        if (!godtaOverlapp && this.map { it.periode }.inneholderOverlapp()) {
            throw IllegalArgumentException("Listen inneholder overlappende perioder")
        }
        val verdi: T = this.firstOrNull()!!.verdi
        return this.map { it.periode }.leggSammen(true).map { PeriodeMedVerdi(verdi, it) }
    }

    // Krever at alle elementer i lista har samme verdi for å fungere!
    private fun <T> List<PeriodeMedVerdi<T>>.slåSammenTilstøtendePerioderMedSammeVerdi(): List<PeriodeMedVerdi<T>> =
        this.leggSammenPerioderMedSammeVerdi(false)

    // Krever IKKE at alle elementer i lista har samme verdi for å fungere
    private fun <T> List<PeriodeMedVerdi<T>>.slåSammenTilstøtendePerioder(): List<PeriodeMedVerdi<T>> =
        this
            .groupBy { it.verdi }
            .values
            .flatMap { listeMedLikeVerdier -> listeMedLikeVerdier.slåSammenTilstøtendePerioderMedSammeVerdi() }
            .sortedBy { it.periode.fraOgMed }

    // Krever at alle elementer i lista har samme verdi for å fungere!
    private fun <T> List<PeriodeMedVerdi<T>>.leggTilPeriodeMedSammeVerdi(periodeMedVerdi: PeriodeMedVerdi<T>): List<PeriodeMedVerdi<T>> =
        (this + periodeMedVerdi).leggSammenPerioderMedSammeVerdi(false)

    /**
     * Utvider periodiseringen før og etter nåværende perioder.
     * Merk at vi ignorer ny verdi for overlappende perioder.
     */
    fun utvid(
        verdi: T,
        nyeTotalePeriode: Periode,
    ): Periodisering<T> {
        val nyePerioder = nyeTotalePeriode.trekkFra(listOf(totalePeriode))
        return this.copy(
            perioderMedVerdi =
            (this.perioderMedVerdi + nyePerioder.map { PeriodeMedVerdi(verdi, it) })
                .sortedBy { it.periode.fraOgMed },
        )
    }

    fun krymp(
        nyeTotalePeriode: Periode,
    ): Periodisering<T> {
        if (nyeTotalePeriode == totalePeriode) {
            // Optimalisering
            return this
        }
        require(nyeTotalePeriode.fraOgMed >= totalePeriode.fraOgMed && nyeTotalePeriode.tilOgMed <= totalePeriode.tilOgMed) {
            "Kan ikke krympe, ny periode $nyeTotalePeriode må ligge innenfor $totalePeriode"
        }
        val beholdte = perioderMedVerdi.mapNotNull { periodeMedVerdi ->
            periodeMedVerdi.periode.overlappendePeriode(nyeTotalePeriode)?.let { overlappendePeriode ->
                periodeMedVerdi.copy(periode = overlappendePeriode)
            }
        }
        return this.copy(
            perioderMedVerdi = beholdte,
        )
    }

    // returnerer hele perioden uten å krympe den hvis det er overlapp
    fun overlapperMed(periode: Periode): Periodisering<T> {
        return Periodisering(perioderMedVerdi.filter { it.periode.overlapperMed(periode) })
    }

    // som krymp, men uten validering av at den nye perioden er innenfor opprinnelig total periode
    fun overlappendePeriode(
        nyeTotalePeriode: Periode,
    ): Periodisering<T> {
        val beholdte = perioderMedVerdi.mapNotNull { periodeMedVerdi ->
            periodeMedVerdi.periode.overlappendePeriode(nyeTotalePeriode)?.let { overlappendePeriode ->
                periodeMedVerdi.copy(periode = overlappendePeriode)
            }
        }
        return this.copy(
            perioderMedVerdi = beholdte,
        )
    }

    /** Sjekker om alle verdiene er lik angitt verdi. */
    fun inneholderKun(verdi: T): Boolean = perioderMedVerdi.all { it.verdi == verdi }

    fun hentVerdiForDag(dag: LocalDate): T? = perioderMedVerdi.firstOrNull { it.periode.inneholder(dag) }?.verdi

    override fun toString(): String =
        "Periodisering(totalePeriode=$totalePeriode, perioderMedVerdi=${
            perioderMedVerdi.map { "\n" + it.toString() }
        })"
}
