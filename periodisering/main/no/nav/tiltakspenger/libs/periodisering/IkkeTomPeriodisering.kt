package no.nav.tiltakspenger.libs.periodisering

import arrow.core.Nel

/**
 * Samler [SammenhengendePeriodisering] og [IkkesammenhengendePeriodisering] i et felles konsept.
 */
sealed interface IkkeTomPeriodisering<T : Any> : Periodisering<T> {
    override val perioderMedVerdi: Nel<PeriodeMedVerdi<T>>
}
