package no.nav.tiltakspenger.libs.ktor.common.oppstart

/**
 * En verdi som kan være forskjellig i NAIS og lokalt, resolvet via [forMiljø].
 *
 * Lar konsumenten uttrykke miljøavhengige verdier (f.eks. kortere intervaller lokalt for rask feedback, lengre i NAIS) uten selv å måtte greine på `isNais` på hvert kallsted.
 * Kan også brukes når man bygger egne [no.nav.tiltakspenger.libs.jobber.TaskGruppe]-er, ikke bare for [Task].
 */
class Miljøverdi<T> private constructor(
    private val nais: T,
    private val lokal: T,
) {
    fun forMiljø(isNais: Boolean): T = if (isNais) nais else lokal

    companion object {
        /** Samme verdi i NAIS og lokalt. */
        fun <T> lik(verdi: T): Miljøverdi<T> = Miljøverdi(nais = verdi, lokal = verdi)

        /** Ulik verdi i NAIS og lokalt. */
        fun <T> ulik(nais: T, lokal: T): Miljøverdi<T> = Miljøverdi(nais = nais, lokal = lokal)
    }
}
