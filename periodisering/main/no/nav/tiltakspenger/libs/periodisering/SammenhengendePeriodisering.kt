package no.nav.tiltakspenger.libs.periodisering

import arrow.core.Nel
import arrow.core.toNonEmptyListOrThrow

/**
 * Denne klassen representerer en sammenhengende periode som kan ha ulike verdier for ulike deler av perioden.
 * Perioden kan ikke ha "hull" som ikke har en verdi og periodene kan ikke overlappe.
 * Periodene må være sortert.
 */
data class SammenhengendePeriodisering<T : Any>(
    override val perioderMedVerdi: Nel<PeriodeMedVerdi<T>>,
) : List<PeriodeMedVerdi<T>> by perioderMedVerdi,
    IkkeTomPeriodisering<T> {
    constructor(vararg periodeMedVerdi: PeriodeMedVerdi<T>) : this(periodeMedVerdi.toList().toNonEmptyListOrThrow())

    constructor(
        initiellVerdi: T,
        totalPeriode: Periode,
    ) : this(PeriodeMedVerdi(initiellVerdi, totalPeriode))

    init {
        require(perioderMedVerdi.erSammenhengende()) {
            "Ugyldig sammenhengende periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte dagen etter periode n slutter. Perioder: ${this.perioder}"
        }
    }

    override val erSammenhengende: Boolean = true

    override fun slåSammenTilstøtendePerioder(): SammenhengendePeriodisering<T> {
        return perioderMedVerdi.slåSammenTilstøtendePerioder().tilSammenhengendePeriodisering()
    }

    override fun <U : Any, V : Any> kombiner(
        other: Periodisering<U>,
        transform: (T, U) -> V,
    ): SammenhengendePeriodisering<V> {
        return super.kombiner(other, transform).tilSammenhengendePeriodisering()
    }

    override fun <U : Any> map(transform: (T, Periode) -> U): SammenhengendePeriodisering<U> {
        return super.map(transform) as SammenhengendePeriodisering
    }

    override fun <U : Any> mapVerdi(transform: (T, Periode) -> U): SammenhengendePeriodisering<U> {
        return super.map(transform) as SammenhengendePeriodisering
    }

    override fun <U : Any> flatMap(transform: (PeriodeMedVerdi<T>) -> List<PeriodeMedVerdi<U>>): SammenhengendePeriodisering<U> {
        return super.flatMap(transform) as SammenhengendePeriodisering
    }

    override fun <U : Any> flatMapPeriodisering(transform: (PeriodeMedVerdi<T>) -> Periodisering<U>): SammenhengendePeriodisering<U> {
        return super.flatMapPeriodisering(transform) as SammenhengendePeriodisering
    }

    override fun toString(): String {
        return "SammenhengendePeriodisering(totalPeriode=$totalPeriode, perioderMedVerdi=${
            perioderMedVerdi.map { "\n" + it.toString() }
        })"
    }
}
