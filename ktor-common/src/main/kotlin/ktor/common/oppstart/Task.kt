package no.nav.tiltakspenger.libs.ktor.common.oppstart

import arrow.core.nonEmptyListOf
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.jobber.GruppertTaskExecutor
import no.nav.tiltakspenger.libs.jobber.TaskGruppe
import no.nav.tiltakspenger.libs.jobber.TaskResultat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * En enkelt skedulert jobb med sitt eget navn, intervall og initialDelay.
 *
 * Dette er det enkle inngangspunktet (via [Bakgrunnsprosessoppsett.tasks]): hver [Task] blir sin egen serielle [TaskGruppe].
 * To [Task]-er (eller andre serielle grupper) kjører aldri samtidig, men hver har sin egen fixed-delay-kadens.
 * Trenger du å batche flere lambdaer i én gruppe, kjøre parallelt, eller drenere kontinuerlig, bruk [Bakgrunnsprosessoppsett.taskGrupper] med [TaskGruppe] direkte.
 *
 * @param navn Unikt navn på jobben; brukes i logging og må være unikt på tvers av alle [Task]-er og [TaskGruppe]-r.
 * @param intervall Ventetid mellom kjøringer (fixed-delay, målt fra *etter* forrige kjøring).
 * Default [GruppertTaskExecutor.STANDARD_JOBB_INTERVALL] (10s) i begge miljø.
 * @param initialDelay Ventetid før første kjøring.
 * Default 1 minutt i NAIS (så appen rekker å starte) og 1 sekund lokalt (rask feedback).
 * @param utfør Selve jobben.
 * Wrappes i correlationId og feilhåndtering av [GruppertTaskExecutor].
 * Returner [TaskResultat.IngenArbeid] når det ikke fantes noe å gjøre (gir samlet «ingen arbeid»-logging per runde), [TaskResultat.Ferdig] når du utførte arbeid og er à jour, [TaskResultat.Feilet] når jobben feilet og selv har logget feilen, eller [TaskResultat.MerArbeid] for å utnytte kontinuerlig drenering.
 */
class Task(
    val navn: String,
    val intervall: Miljøverdi<Duration> = Miljøverdi.lik(GruppertTaskExecutor.STANDARD_JOBB_INTERVALL),
    val initialDelay: Miljøverdi<Duration> = Miljøverdi.ulik(nais = 1.minutes, lokal = 1.seconds),
    val utfør: suspend (CorrelationId) -> TaskResultat,
) {
    internal fun tilTaskGruppe(isNais: Boolean): TaskGruppe = TaskGruppe(
        navn = navn,
        intervall = intervall.forMiljø(isNais),
        tasks = nonEmptyListOf(utfør),
        initialDelay = initialDelay.forMiljø(isNais),
    )
}
