package no.nav.tiltakspenger.libs.periodisering

import arrow.core.toNonEmptyListOrThrow
import no.nav.tiltakspenger.libs.common.singleOrNullOrThrow
import java.time.LocalDate

/**
 * Kan være tom, ikke-sammenhengende eller sammenhengende.
 */
interface Periodisering<T : Any> : List<PeriodeMedVerdi<T>> {
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

        @JvmName("invokeFromPeriodeMedVerdiVararg")
        operator fun <T : Any> invoke(
            vararg periodeMedVerdi: PeriodeMedVerdi<T>,
        ) = periodeMedVerdi.toList().tilPeriodisering()

        @JvmName("invokeFromPeriodeMedVerdiList")
        operator fun <T : Any> invoke(
            perioderMedVerdi: List<PeriodeMedVerdi<T>>,
        ) = perioderMedVerdi.toList().tilPeriodisering()

        @JvmName("invokeFromPeriodiserbar")
        operator fun <T : Periodiserbar> invoke(
            vararg periode: T,
        ) = periode.map { PeriodeMedVerdi(it, it.periode) }.tilPeriodisering()

        @JvmName("invokeFromPeriodiserbarList")
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

    /**
     * Støtter at den nye perioden er helt eller delvis utenfor. Da vil man få en [TomPeriodisering] eller [IkkesammenhengendePeriodisering]
     */
    fun krymp(nyPeriode: Periode): Periodisering<T> {
        if (nyPeriode == totalPeriode) return this
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
    fun utvid(verdi: T, nyTotalPeriode: Periode): IkkeTomPeriodisering<T> {
        val nyePerioder = nyTotalPeriode.trekkFra(listOf(totalPeriode))
        return (this.perioderMedVerdi + nyePerioder.map { PeriodeMedVerdi(verdi, it) })
            .sortedBy { it.periode.fraOgMed }.tilIkkeTomPeriodisering()
    }

    /**
     * Utvider en periodisering. Dersom periodiseringen har hull, vil disse fylles inn.
     * Merk at hvis Periodiseringen er en [IkkesammenhengendePeriodisering], så blir det en [SammenhengendePeriodisering] etter denne operasjonen - bruk [utvid] hvis du ønsker å beholde hullene i den originale periodiseringen
     */
    fun utvidOgFyllInnAlleTommePerioder(verdi: T, nyTotalPeriode: Periode): SammenhengendePeriodisering<T> {
        val nyFørstePeriode = nyTotalPeriode.fraOgMedTil(this.perioder.first().fraOgMed.minusDays(1))
            ?.let { PeriodeMedVerdi(verdi, it) }

        val perioderMedFylltInnHull = this.perioderMedVerdi.fold(mutableListOf<PeriodeMedVerdi<T>>()) { acc, current ->
            val previous = acc.lastOrNull()
            if (previous == null) {
                acc.add(current)
            } else {
                if (!previous.periode.tilstøter(current.periode)) {
                    acc.add(
                        PeriodeMedVerdi(
                            verdi,
                            Periode(
                                previous.periode.tilOgMed.plusDays(1),
                                current.periode.fraOgMed.minusDays(1),
                            ),
                        ),
                    )
                }
                acc.add(current)
            }

            acc
        }

        val nySistePeriode = Periode(
            this.perioder.last().fraOgMed,
            this.perioder.last().tilOgMed.plusDays(1),
        ).tilOgMedTil(nyTotalPeriode.tilOgMed)?.let {
            PeriodeMedVerdi(verdi, it)
        }

        return (listOf(nyFørstePeriode) + perioderMedFylltInnHull + listOf(nySistePeriode))
            .mapNotNull { it }
            .tilSammenhengendePeriodisering()
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
    fun setVerdiForDelperiode(verdi: T, delPeriode: Periode): IkkeTomPeriodisering<T> {
        return this.perioderMedVerdi.setVerdiForDelperiode(verdi, delPeriode).tilIkkeTomPeriodisering()
    }

    fun <U : Any, V : Any> kombiner(
        other: Periodisering<U>,
        transform: (T, U) -> V,
    ): Periodisering<V> {
        return this.perioderMedVerdi.kombiner(other.perioderMedVerdi, transform).tilPeriodisering()
    }

    fun tilPair(): List<Pair<Periode, T>> {
        return this.perioderMedVerdi.map { Pair(it.periode, it.verdi) }
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

fun <T : Any> List<PeriodeMedVerdi<T>>.tilIkkeTomPeriodisering(): IkkeTomPeriodisering<T> {
    return tilPeriodisering() as IkkeTomPeriodisering
}

/**
 * Slår sammen tilstøtende perioder.
 * Støtter ikke overlapp.
 * Støtter hull.
 */
@JvmName("tilPeriodiseringFraPeriodeMedVerdiList")
fun <T : Any> List<PeriodeMedVerdi<T>>.tilPeriodisering(): Periodisering<T> {
    if (isEmpty()) return TomPeriodisering.instance()
    return if (erSammenhengende()) {
        SammenhengendePeriodisering(this.slåSammenTilstøtendePerioder().toNonEmptyListOrThrow())
    } else {
        IkkesammenhengendePeriodisering(this.slåSammenTilstøtendePerioder().toNonEmptyListOrThrow())
    }
}

@JvmName("tilPeriodiseringFraPair")
fun <T : Any> List<Pair<Periode, T>>.tilPeriodisering(): Periodisering<T> {
    return this.map { PeriodeMedVerdi(it.second, it.first) }.tilPeriodisering()
}

fun <T : Any> List<Pair<T, Periode>>.tilPeriodisering(): Periodisering<T> {
    return this.map { PeriodeMedVerdi(it.first, it.second) }.tilPeriodisering()
}
