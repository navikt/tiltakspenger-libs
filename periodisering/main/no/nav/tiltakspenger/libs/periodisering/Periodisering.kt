package no.nav.tiltakspenger.libs.periodisering

/*
Denne klassen representerer en sammenhengende periode som kan ha ulike verdier for ulike deler av perioden.
Perioden kan ikke ha "hull" som ikke har en verdi
 */
data class Periodisering<T> private constructor(
    val totalePeriode: Periode,
    private val defaultVerdi: T,
    private val perioderMedVerdi: List<PeriodeMedVerdi<T>>,
) {
    companion object {
        operator fun <T> invoke(
            defaultVerdi: T,
            totalePeriode: Periode,
        ): Periodisering<T> {
            return Periodisering(
                totalePeriode = totalePeriode,
                defaultVerdi = defaultVerdi,
                perioderMedVerdi =
                listOf(PeriodeMedVerdi(defaultVerdi, totalePeriode)),
            )
        }

        fun <T> List<Periodisering<T>>.reduser(sammensattVerdi: (T, T) -> T): Periodisering<T> {
            return this.reduce { total: Periodisering<T>, next: Periodisering<T> ->
                total.kombiner(next, sammensattVerdi).slåSammenTilstøtendePerioder()
            }
        }
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
            other.perioderMedVerdi.mapNotNull { otherPeriodeMedVerdi ->
                thisPeriodeMedVerdi.periode.overlappendePeriode(otherPeriodeMedVerdi.periode)?.let {
                    PeriodeMedVerdi(sammensattVerdi(thisPeriodeMedVerdi.verdi, otherPeriodeMedVerdi.verdi), it)
                }
            }
        }.let {
            Periodisering(
                this.totalePeriode,
                sammensattVerdi(this.defaultVerdi, other.defaultVerdi),
                it,
            )
        }
    }

    fun <U> map(ekstrahertVerdi: (T) -> U): Periodisering<U> =
        this.perioderMedVerdi
            .map { PeriodeMedVerdi(ekstrahertVerdi(it.verdi), it.periode) }
            .slåSammenTilstøtendePerioder()
            .let { Periodisering(this.totalePeriode, ekstrahertVerdi(this.defaultVerdi), it) }

    fun perioder(): List<PeriodeMedVerdi<T>> = perioderMedVerdi.sortedBy { it.periode.fra }

    // Private hjelpemetoder:

    private fun setPeriodeMedVerdi(delPeriodeMedVerdi: PeriodeMedVerdi<T>): Periodisering<T> {
        if (!totalePeriode.inneholderHele(delPeriodeMedVerdi.periode)) {
            throw IllegalArgumentException("Angitt periode ${delPeriodeMedVerdi.periode} er ikke innenfor $totalePeriode")
        }
        val nyePerioderMedSammeVerdi = perioderMedVerdi
            .perioderMedSammeVerdi(delPeriodeMedVerdi.verdi)
            .trekkFra(delPeriodeMedVerdi)
            .leggTilPeriodeMedSammeVerdi(delPeriodeMedVerdi)
        val nyePerioderMedUlikVerdi = perioderMedVerdi
            .perioderMedUlikVerdi(delPeriodeMedVerdi.verdi)
            .trekkFra(delPeriodeMedVerdi)
        return Periodisering(
            totalePeriode,
            defaultVerdi,
            nyePerioderMedSammeVerdi + nyePerioderMedUlikVerdi,
        )
    }

    // Krever IKKE at alle elementer i lista har samme verdi for å fungere
    private fun <T> List<PeriodeMedVerdi<T>>.trekkFra(periodeMedVerdi: PeriodeMedVerdi<T>): List<PeriodeMedVerdi<T>> =
        this.trekkFra(periodeMedVerdi.periode)

    // Krever at alle elementer i lista har samme verdi for å fungere!
    private fun <T> List<PeriodeMedVerdi<T>>.leggSammenPerioderMedSammeVerdi(godtaOverlapp: Boolean = true): List<PeriodeMedVerdi<T>> {
        if (!this.allePerioderHarSammeVerdi()) {
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
        this.groupBy { it.verdi }
            .values
            .flatMap { listeMedLikeVerdier -> listeMedLikeVerdier.slåSammenTilstøtendePerioderMedSammeVerdi() }

    // Krever at alle elementer i lista har samme verdi for å fungere!
    private fun <T> List<PeriodeMedVerdi<T>>.leggTilPeriodeMedSammeVerdi(periodeMedVerdi: PeriodeMedVerdi<T>): List<PeriodeMedVerdi<T>> =
        (this + periodeMedVerdi).leggSammenPerioderMedSammeVerdi(false)

    override fun toString(): String {
        return "PeriodeMedVerdier(totalePeriode=$totalePeriode, defaultVerdi=$defaultVerdi, perioderMedVerdi=${
            perioderMedVerdi.sortedBy { it.periode.fra }.map { "\n" + it.toString() }
        })"
    }

    fun utvid(nyeTotalePeriode: Periode): Periodisering<T> {
        require(nyeTotalePeriode.inneholderHele(totalePeriode)) { "Kan ikke utvide, ny periode $nyeTotalePeriode inneholder ikke hele den eksisterende perioden $totalePeriode" }
        val nyePerioder = nyeTotalePeriode.trekkFra(listOf(totalePeriode))
        return this.copy(
            totalePeriode = nyeTotalePeriode,
            defaultVerdi = this.defaultVerdi,
            perioderMedVerdi = (this.perioderMedVerdi + nyePerioder.map { PeriodeMedVerdi(defaultVerdi, it) })
                .sortedBy { it.periode.fra },
        )
    }
}
