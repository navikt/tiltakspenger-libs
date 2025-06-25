package no.nav.tiltakspenger.libs.periodisering

import arrow.core.toNonEmptyListOrThrow
import no.nav.tiltakspenger.libs.common.singleOrNullOrThrow
import java.time.LocalDate
import kotlin.collections.maxOf
import kotlin.collections.minOf

/**
 * Kan være tom, ikke-sammenhengende eller sammenhengende.
 */
sealed interface Periodisering<T : Any> : List<PeriodeMedVerdi<T>> {
    val perioderMedVerdi: List<PeriodeMedVerdi<T>>

    /**
     * Periodiseringens totale periode; dvs. tidligste fra og med til seneste til og med.
     * Sier ikke noe om periodiseringen er sammenhengende eller ikke.
     *
     * @throws NoSuchElementException dersom [perioderMedVerdi] er tom.
     */
    val totalPeriode: Periode
        get() = Periode(
            perioder.minOf { it.fraOgMed },
            perioder.maxOf { it.tilOgMed },
        )
    val perioder: List<Periode> get() = perioderMedVerdi.map { it.periode }
    val verdier: List<T> get() = perioderMedVerdi.map { it.verdi }
    val erSammenhengende: Boolean

    companion object {

        operator fun <T : Any> invoke(
            initiellVerdi: T,
            totalPeriode: Periode,
        ) = listOf(PeriodeMedVerdi(initiellVerdi, totalPeriode)).tilPeriodisering()

        operator fun <T : Any> invoke(
            vararg periodeMedVerdi: PeriodeMedVerdi<T>,
        ) = periodeMedVerdi.toList().tilPeriodisering()

        operator fun <T : Periodiserbar> invoke(
            vararg periode: T,
        ) = periode.map { PeriodeMedVerdi(it, it.periode) }.tilPeriodisering()

        operator fun <T : Periodiserbar> invoke(
            perioder: List<T>,
        ) = perioder.map { PeriodeMedVerdi(it, it.periode) }.tilPeriodisering()

        fun <T : Any> empty(): TomPeriodisering<T> = TomPeriodisering.instance()
    }

    /** Sjekker om alle verdiene er lik angitt verdi. */
    fun inneholderKun(verdi: T): Boolean = perioderMedVerdi.all { it.verdi == verdi }
    fun hentVerdiForDag(dag: LocalDate): T? = perioderMedVerdi.singleOrNullOrThrow { it.periode.inneholder(dag) }?.verdi
    fun overlapper(other: Periode): Boolean = perioderMedVerdi.overlapper(other)
    fun overlapper(other: Periodisering<*>): Boolean = perioderMedVerdi.overlapper(other.perioderMedVerdi)
    fun overlappendePeriode(periode: Periode): Periodisering<T> =
        perioderMedVerdi.overlappendePeriode(periode).tilPeriodisering()

    /** [nyPeriode] må ligge innenfor den gamle perioden sin totalperiode */
    fun krymp(nyPeriode: Periode): Periodisering<T> {
        if (nyPeriode == totalPeriode) return this
        require(nyPeriode.fraOgMed >= totalPeriode.fraOgMed && nyPeriode.tilOgMed <= totalPeriode.tilOgMed) {
            "Kan ikke krympe, ny periode $nyPeriode må ligge innenfor $totalPeriode"
        }
        val beholdte = perioderMedVerdi.mapNotNull { periodeMedVerdi ->
            periodeMedVerdi.periode.overlappendePeriode(nyPeriode)?.let { overlappendePeriode ->
                periodeMedVerdi.copy(periode = overlappendePeriode)
            }
        }
        return beholdte.tilPeriodisering()
    }

    /**
     * Utvider periodiseringen før og etter nåværende perioder.
     * Merk at vi ignorer ny verdi for overlappende perioder.
     * @return [SammenhengendePeriodisering] eller [IkkesammenhengendePeriodisering]
     */
    fun utvid(verdi: T, nyTotalPeriode: Periode): Periodisering<T> {
        val nyePerioder = nyTotalPeriode.trekkFra(listOf(totalPeriode))
        return (this.perioderMedVerdi + nyePerioder.map { PeriodeMedVerdi(verdi, it) })
            .sortedBy { it.periode.fraOgMed }.tilPeriodisering()
    }

    /**
     * Merk at vi ikke vil kaste feil, selvom den nye perioden ikke overlapper den gamle.
     * Tenkt at denne erstatter kombinasjonsbruk av utvid + krymp.
     * Dersom det ikke gir mening og sende inn en default-verdi (i de tilfellene periodiseringen vil overlappe den nye perioden), kan man heller bruke [krymp]
     *
     * @return En ny periodisering med perioden til den innsendte [periode]. Dersom verdien finnes i nåværende periodisering beholdes den, hvis den ikke finnes får dagen den innsendte [defaultVerdiDersomDenMangler]
     */
    fun nyPeriode(periode: Periode, defaultVerdiDersomDenMangler: T): SammenhengendePeriodisering<T> {
        return periode.tilDager().map { dag ->
            val verdi = perioderMedVerdi.singleOrNullOrThrow { it.periode.inneholder(dag) }?.verdi
                ?: defaultVerdiDersomDenMangler
            PeriodeMedVerdi(verdi, dag.somPeriode())
        }.tilSammenhengendePeriodisering()
    }

    /**
     * Merk at denne kan kaste dersom de nye periodene overlapper eller man går fra [SammenhengendePeriodisering] til [IkkesammenhengendePeriodisering] eller omvendt.
     * Merk at denne ikke krymper periodene.
     */
    fun <U : Any> flatMap(transform: (PeriodeMedVerdi<T>) -> List<PeriodeMedVerdi<U>>): Periodisering<U> =
        perioderMedVerdi.flatMap { transform(it) }.tilPeriodisering()

    /**
     * Merk at denne kan kaste dersom de nye periodene overlapper eller man går fra [SammenhengendePeriodisering] til [IkkesammenhengendePeriodisering] eller omvendt.
     * Merk at denne krymper periodene.
     */
    fun <U : Any> flatMapPeriodisering(transform: (PeriodeMedVerdi<T>) -> Periodisering<U>): Periodisering<U> =
        perioderMedVerdi.flatMap { transform(it).krymp(it.periode).perioderMedVerdi }.tilPeriodisering()

    /** Her kan man gå fra [SammenhengendePeriodisering] til [IkkesammenhengendePeriodisering] eller [TomPeriodisering] basert på hvor mye man filtrerer bort. */
    fun filter(predicate: (T, Periode) -> Boolean): Periodisering<T> =
        perioderMedVerdi.filter { predicate(it.verdi, it.periode) }.tilPeriodisering()

    /** Her kan man gå fra [SammenhengendePeriodisering] til [IkkesammenhengendePeriodisering] eller [TomPeriodisering] basert på hvor mye man filtrerer bort. */
    fun filter(predicate: (PeriodeMedVerdi<T>) -> Boolean): Periodisering<T> =
        perioderMedVerdi.filter { predicate(it) }.tilPeriodisering()

    /** Her kan man gå fra [SammenhengendePeriodisering] til [IkkesammenhengendePeriodisering] eller [TomPeriodisering] basert på hvor mye man filtrerer bort. */
    fun filterNot(predicate: (T, Periode) -> Boolean): Periodisering<T> =
        perioderMedVerdi.filterNot { predicate(it.verdi, it.periode) }.tilPeriodisering()

    /** Her kan man gå fra [SammenhengendePeriodisering] til [IkkesammenhengendePeriodisering] eller [TomPeriodisering] basert på hvor mye man filtrerer bort. */
    fun filterNot(predicate: (PeriodeMedVerdi<T>) -> Boolean): Periodisering<T> =
        perioderMedVerdi.filterNot { predicate(it) }.tilPeriodisering()

    fun <U : Any> mapVerdi(transform: (T, Periode) -> U): Periodisering<U> =
        perioderMedVerdi.map { PeriodeMedVerdi(transform(it.verdi, it.periode), it.periode) }.tilPeriodisering()

    fun <U : Any> map(transform: (T, Periode) -> U): Periodisering<U> =
        perioderMedVerdi.map { PeriodeMedVerdi(transform(it.verdi, it.periode), it.periode) }.tilPeriodisering()

    fun <U : Any> map(transform: (PeriodeMedVerdi<T>) -> U): Periodisering<U> =
        perioderMedVerdi.map { PeriodeMedVerdi(transform(it), it.periode) }.tilPeriodisering()

    fun slåSammenTilstøtendePerioder(): Periodisering<T>

    /**
     * Overskriver eksisterende perioder og støtter hull.
     * Dersom denne er tom vil den returnere [SammenhengendePeriodisering] for innsendt periode.
     * Dersom den nye perioden ikke overlapper med eksisterende periode, returneres en [IkkesammenhengendePeriodisering]
     * Returnerer aldri en [TomPeriodisering]
     */
    fun setVerdiForDelperiode(verdi: T, delPeriode: Periode): Periodisering<T> {
        return this.perioderMedVerdi.setVerdiForDelperiode(verdi, delPeriode).tilPeriodisering()
    }

    fun <U : Any, V : Any> kombiner(
        other: Periodisering<U>,
        transform: (T, U) -> V,
    ): Periodisering<V> {
        return this.perioderMedVerdi.kombiner(other.perioderMedVerdi, transform).tilPeriodisering()
    }
}

fun <T : Any> List<PeriodeMedVerdi<T>>.tilSammenhengendePeriodisering(): SammenhengendePeriodisering<T> {
    return tilPeriodisering() as SammenhengendePeriodisering
}

fun <T : Any> List<PeriodeMedVerdi<T>>.tilIkkesammenhengendePeriodisering(): IkkesammenhengendePeriodisering<T> {
    return tilPeriodisering() as IkkesammenhengendePeriodisering
}

fun <T : Any> List<PeriodeMedVerdi<T>>.tilTomPeriodisering(): TomPeriodisering<T> {
    return tilPeriodisering() as TomPeriodisering
}

/**
 * Slår sammen tilstøtende perioder.
 * Støtter ikke overlapp.
 * Støtter hull.
 */
fun <T : Any> List<PeriodeMedVerdi<T>>.tilPeriodisering(): Periodisering<T> {
    if (isEmpty()) return TomPeriodisering.instance()
    return if (erSammenhengende()) {
        SammenhengendePeriodisering(this.slåSammenTilstøtendePerioder().toNonEmptyListOrThrow())
    } else {
        IkkesammenhengendePeriodisering(this.slåSammenTilstøtendePerioder().toNonEmptyListOrThrow())
    }
}
