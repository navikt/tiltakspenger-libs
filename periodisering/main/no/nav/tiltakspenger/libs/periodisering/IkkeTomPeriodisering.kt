package no.nav.tiltakspenger.libs.periodisering

import arrow.core.Nel

/**
 * Samler [SammenhengendePeriodisering] og [IkkesammenhengendePeriodisering] i et felles konsept.
 */
sealed interface IkkeTomPeriodisering<T : Any> : Periodisering<T> {
    override val perioderMedVerdi: Nel<PeriodeMedVerdi<T>>
    override val perioder: Nel<Periode>

    override fun <U : Any> flatMap(transform: (PeriodeMedVerdi<T>) -> List<PeriodeMedVerdi<U>>): IkkeTomPeriodisering<U> {
        return super.flatMap(transform) as IkkeTomPeriodisering
    }

    override fun <U : Any> flatMapPeriodisering(transform: (PeriodeMedVerdi<T>) -> Periodisering<U>): IkkeTomPeriodisering<U> {
        return super.flatMapPeriodisering(transform) as IkkeTomPeriodisering
    }

    override fun <U : Any> map(transform: (PeriodeMedVerdi<T>) -> U): IkkeTomPeriodisering<U> {
        return super.map(transform) as IkkeTomPeriodisering
    }

    override fun <U : Any> map(transform: (T, Periode) -> U): IkkeTomPeriodisering<U> {
        return super.map(transform) as IkkeTomPeriodisering
    }

    override fun <U : Any> mapVerdi(transform: (T, Periode) -> U): IkkeTomPeriodisering<U> {
        return super.mapVerdi(transform) as IkkeTomPeriodisering
    }

    override fun slåSammenTilstøtendePerioder(): IkkeTomPeriodisering<T>

    override fun <U : Any, V : Any> kombiner(other: Periodisering<U>, transform: (T, U) -> V): IkkeTomPeriodisering<V> {
        return super.kombiner(other, transform) as IkkeTomPeriodisering
    }
}
