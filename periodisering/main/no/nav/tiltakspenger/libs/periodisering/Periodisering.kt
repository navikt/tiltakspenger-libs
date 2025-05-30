package no.nav.tiltakspenger.libs.periodisering

import no.nav.tiltakspenger.libs.common.singleOrNullOrThrow
import java.time.LocalDate

/**
 * Denne klassen representerer en sammenhengende periode som kan ha ulike verdier for ulike deler av perioden.
 * Perioden kan ikke ha "hull" som ikke har en verdi og periodene kan ikke overlappe.
 * Periodene må være sortert.
 */
data class Periodisering<T>(
    val perioderMedVerdi: List<PeriodeMedVerdi<T>>,
) : List<PeriodeMedVerdi<T>> by perioderMedVerdi {
    constructor(vararg periodeMedVerdi: PeriodeMedVerdi<T>) : this(periodeMedVerdi.toList())

    constructor(
        initiellVerdi: T,
        totalPeriode: Periode,
    ) : this(PeriodeMedVerdi(initiellVerdi, totalPeriode))

    val perioder: List<Periode> by lazy { perioderMedVerdi.map { it.periode } }
    val verdier: List<T> by lazy { perioderMedVerdi.map { it.verdi } }

    init {
        require(
            zipWithNext()
                .all { it.second.periode.fraOgMed == it.first.periode.tilOgMed.plusDays(1) },
        ) { "Ugyldig periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte dagen etter periode n slutter. Perioder: ${this.perioder}" }
    }

    val totalPeriode by lazy {
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
        if (totalPeriode != other.totalPeriode) {
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

    fun <U> map(kombinertVerdi: (T, Periode) -> U): Periodisering<U> {
        return this.perioderMedVerdi
            .map { PeriodeMedVerdi(kombinertVerdi(it.verdi, it.periode), it.periode) }
            .slåSammenTilstøtendePerioder()
            .let { Periodisering(it) }
    }

    /** Oppdaterer kun verdien for hver [PeriodeMedVerdi] */
    fun <U> mapVerdi(kombinertVerdi: (T, Periode) -> U): Periodisering<U> {
        return this.perioderMedVerdi
            .map { PeriodeMedVerdi(kombinertVerdi(it.verdi, it.periode), it.periode) }
            .slåSammenTilstøtendePerioder()
            .let { Periodisering(it) }
    }

    /**
     * Merk at en Periodisering må være sammenhengende. Så for at det skal gi mening å bruke denne metoden må [T] være nullable.
     */
    fun filter(kombinertVerdi: (T, Periode) -> Boolean): Periodisering<T> {
        return this.perioderMedVerdi.map {
            if (kombinertVerdi(it.verdi, it.periode)) {
                it
            } else {
                PeriodeMedVerdi(null as T, it.periode)
            }
        }.slåSammenTilstøtendePerioder()
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
        if (!totalPeriode.inneholderHele(delPeriodeMedVerdi.periode)) {
            throw IllegalArgumentException("Angitt periode ${delPeriodeMedVerdi.periode} er ikke innenfor $totalPeriode")
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
    private fun <T> List<PeriodeMedVerdi<T>>.leggTilPeriodeMedSammeVerdi(periodeMedVerdi: PeriodeMedVerdi<T>): List<PeriodeMedVerdi<T>> {
        return (this + periodeMedVerdi).leggSammenPerioderMedSammeVerdi(false)
    }

    /**
     * Merk at vi ikke vil kaste feil, selvom den nye perioden ikke overlapper den gamle.
     * Tenkt at denne erstatter kombinasjonsbruk av utvid + krymp.
     * Dersom det ikke gir mening og sende inn en default-verdi (i de tilfellene periodiseringen vil overlappe den nye perioden), kan man heller bruke [krymp]
     *
     * @return En ny periodisering med perioden til den innsendte [periode]. Dersom verdien finnes i nåværende periodisering beholdes den, hvis den ikke finnes får dagen den innsendte [defaultVerdiDersomDenMangler]
     * @throws IllegalArgumentException dersom den nåværende periodiseringen inneholder overlapp for angitt periode.
     */
    fun nyPeriode(
        periode: Periode,
        defaultVerdiDersomDenMangler: T,
    ): Periodisering<T> {
        return Periodisering(
            periode.tilDager().map { dag ->
                val verdi = perioderMedVerdi.singleOrNullOrThrow { it.periode.inneholder(dag) }?.verdi
                    ?: defaultVerdiDersomDenMangler
                PeriodeMedVerdi(verdi, Periode(dag, dag))
            }.slåSammenTilstøtendePerioder(),
        )
    }

    /**
     * Utvider periodiseringen før og etter nåværende perioder.
     * Merk at vi ignorer ny verdi for overlappende perioder.
     */
    fun utvid(
        verdi: T,
        nyTotalPeriode: Periode,
    ): Periodisering<T> {
        val nyePerioder = nyTotalPeriode.trekkFra(listOf(totalPeriode))
        return this.copy(
            perioderMedVerdi =
            (this.perioderMedVerdi + nyePerioder.map { PeriodeMedVerdi(verdi, it) })
                .sortedBy { it.periode.fraOgMed },
        )
    }

    fun krymp(
        nyTotalPeriode: Periode,
    ): Periodisering<T> {
        if (nyTotalPeriode == totalPeriode) {
            // Optimalisering
            return this
        }
        require(nyTotalPeriode.fraOgMed >= totalPeriode.fraOgMed && nyTotalPeriode.tilOgMed <= totalPeriode.tilOgMed) {
            "Kan ikke krympe, ny periode $nyTotalPeriode må ligge innenfor $totalPeriode"
        }
        val beholdte = perioderMedVerdi.mapNotNull { periodeMedVerdi ->
            periodeMedVerdi.periode.overlappendePeriode(nyTotalPeriode)?.let { overlappendePeriode ->
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
        nyTotalPeriode: Periode,
    ): Periodisering<T> {
        val beholdte = perioderMedVerdi.mapNotNull { periodeMedVerdi ->
            periodeMedVerdi.periode.overlappendePeriode(nyTotalPeriode)?.let { overlappendePeriode ->
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
        "Periodisering(totalPeriode=$totalPeriode, perioderMedVerdi=${
            perioderMedVerdi.map { "\n" + it.toString() }
        })"
}
