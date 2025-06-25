package no.nav.tiltakspenger.libs.periodisering

class TomPeriodisering<T : Any> private constructor() :
    List<PeriodeMedVerdi<T>> by emptyList(),
    Periodisering<T> {
    companion object {
        private val INSTANCE = TomPeriodisering<Any>()

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> instance(): TomPeriodisering<T> = INSTANCE as TomPeriodisering<T>
    }

    override val perioderMedVerdi = emptyList<PeriodeMedVerdi<T>>()
    override val erSammenhengende = true
    override fun utvid(verdi: T, nyTotalPeriode: Periode) = SammenhengendePeriodisering(verdi, nyTotalPeriode)
    override fun slåSammenTilstøtendePerioder() = this
    override fun <U : Any, V : Any> kombiner(other: Periodisering<U>, transform: (T, U) -> V) =
        throw IllegalArgumentException("Støtter ikke kombinere en tom periodisering")

    override fun equals(other: Any?) = other is TomPeriodisering<*>
    override fun hashCode(): Int = 0
}
