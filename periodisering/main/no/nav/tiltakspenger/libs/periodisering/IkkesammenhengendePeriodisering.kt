package no.nav.tiltakspenger.libs.periodisering

import arrow.core.Nel
import arrow.core.toNonEmptyListOrThrow

/**
 * Denne klassen representerer en ikke-sammenhengende periode som kan ha ulike verdier for ulike deler av perioden.
 * Den er ikke ment brukt som en type på et felt eller en returtype, bruk heller [Periodisering] som kan ta form som [no.nav.tiltakspenger.libs.periodisering.TomPeriodisering], [IkkesammenhengendePeriodisering] eller [no.nav.tiltakspenger.libs.periodisering.SammenhengendePeriodisering]
 * Periodiseringen kan ha "hull" som ikke har en verdi.
 * Periodene kan ikke overlappe.
 * Periodene må være sortert.
 */
data class IkkesammenhengendePeriodisering<T : Any>(
    override val perioderMedVerdi: Nel<PeriodeMedVerdi<T>>,
) : List<PeriodeMedVerdi<T>> by perioderMedVerdi,
    IkkeTomPeriodisering<T> {
    constructor(vararg periodeMedVerdi: PeriodeMedVerdi<T>) : this(periodeMedVerdi.toList().toNonEmptyListOrThrow())

    init {
        require(
            zipWithNext()
                .all { it.second.periode.fraOgMed > it.first.periode.tilOgMed },
        ) { "Ugyldig ikke-sammenhengende periodisering, for alle perioderMedVerdi gjelder at periode n+1 må starte etter periode n slutter. Perioder: ${this.perioder}" }
        require(!perioderMedVerdi.erSammenhengende()) {
            "En ikke-sammenhengendeperiode kan ikke være sammenhengende. Bruk [Periodisering] eller [SammenhengendePeriodisering] istedenfor. Perioder: ${this.perioder}"
        }
    }

    companion object {
        operator fun <T : Periodiserbar> invoke(
            vararg periode: T,
        ) = IkkesammenhengendePeriodisering(periode.map { PeriodeMedVerdi(it, it.periode) }.toNonEmptyListOrThrow())

        operator fun <T : Periodiserbar> invoke(
            perioder: List<T>,
        ) = IkkesammenhengendePeriodisering(perioder.map { PeriodeMedVerdi(it, it.periode) }.toNonEmptyListOrThrow())
    }

    override val erSammenhengende: Boolean = true

    override fun slåSammenTilstøtendePerioder(): IkkesammenhengendePeriodisering<T> {
        return IkkesammenhengendePeriodisering(perioderMedVerdi = perioderMedVerdi.slåSammenTilstøtendePerioder())
    }

    override fun <U : Any> map(transform: (T, Periode) -> U): IkkesammenhengendePeriodisering<U> {
        return super.map(transform) as IkkesammenhengendePeriodisering
    }

    override fun <U : Any> mapVerdi(transform: (T, Periode) -> U): IkkesammenhengendePeriodisering<U> {
        return super.map(transform) as IkkesammenhengendePeriodisering
    }

    override fun <U : Any> flatMap(transform: (PeriodeMedVerdi<T>) -> List<PeriodeMedVerdi<U>>): IkkesammenhengendePeriodisering<U> {
        return super.flatMap(transform) as IkkesammenhengendePeriodisering
    }

    override fun <U : Any> flatMapPeriodisering(
        transform: (PeriodeMedVerdi<T>) -> Periodisering<U>,
    ): IkkesammenhengendePeriodisering<U> {
        return super.flatMapPeriodisering(transform) as IkkesammenhengendePeriodisering
    }

    override fun toString(): String {
        return "IkkesammenhengendePeriodisering(totalPeriode=$totalPeriode, perioderMedVerdi=${
            perioderMedVerdi.map { "\n" + it.toString() }
        })"
    }
}
