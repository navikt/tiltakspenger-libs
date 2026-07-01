package no.nav.tiltakspenger.libs.jobber

import arrow.core.NonEmptyList
import no.nav.tiltakspenger.libs.common.CorrelationId
import kotlin.time.Duration

/**
 * Hvordan en [TaskGruppe] forholder seg til de andre gruppene i en [GruppertTaskExecutor].
 *
 * Merk: dette styrer forholdet *mellom* grupper.
 * Tasks *innad* i en gruppe kjøres alltid seriellt i listerekkefølge (neste task starter først når forrige er ferdig), slik at vi ikke overbelaster databasen med samtidige spørringer.
 */
enum class Kjøremodus {
    /**
     * Gruppen deler den ene scheduler-tråden med alle andre [SERIELT]-grupper og kjøres etter tur med dem.
     * To serielle grupper kjører aldri samtidig, uansett intervall, og det brukes ingen ekstra tråder/coroutines.
     * Dette er standard og passer jobber som er tunge på databasen.
     */
    SERIELT,

    /**
     * Gruppen kjøres på sin egen coroutine, uavhengig av (parallelt med) alle andre grupper – både serielle og andre parallelle.
     * Passer f.eks. kontinuerlig drenering ([TaskGruppe.kjørKontinuerligTilTom]) der vi vil spise unna en backlog raskt uten å stoppe opp de serielle gruppene.
     */
    PARALLELT,
}

/**
 * Resultatet en task returnerer for å styre kontinuerlig kjøring ([TaskGruppe.kjørKontinuerligTilTom]).
 *
 * Tasks som ikke bryr seg om drenering returnerer [Ferdig].
 */
sealed interface TaskResultat {
    /**
     * Det gjenstår mer arbeid akkurat nå (f.eks. en side-/limit-basert henting fikk fullt resultatsett).
     * I en gruppe med [TaskGruppe.kjørKontinuerligTilTom] fører dette til at gruppen kjøres på nytt umiddelbart.
     */
    data object MerArbeid : TaskResultat

    /** Ingenting mer å gjøre nå – gruppen kan vente til neste intervall. */
    data object Ferdig : TaskResultat
}

/**
 * En navngitt gruppe av tasks som kjøres på et eget [intervall] av en [GruppertTaskExecutor].
 *
 * @param navn Unikt navn i executoren; brukes i logging.
 * @param intervall Ventetid mellom kjøringer, målt fra *etter* at forrige kjøring av gruppen er ferdig (fixed-delay).
 *   Default [GruppertTaskExecutor.STANDARD_JOBB_INTERVALL].
 * @param tasks Tasks som kjøres seriellt i listerekkefølge hver gang gruppen kjører.
 *   En feil i én task stopper ikke de øvrige.
 *   Returner [TaskResultat.Ferdig] når det ikke er mer å gjøre, eller [TaskResultat.MerArbeid] for å utnytte [kjørKontinuerligTilTom].
 * @param kjøremodus Se [Kjøremodus].
 *   Default [Kjøremodus.SERIELT].
 * @param initialDelay Ventetid før første kjøring.
 *   Default [GruppertTaskExecutor.STANDARD_INITIAL_DELAY].
 * @param kjørKontinuerligTilTom Når `true` og en task melder [TaskResultat.MerArbeid], kjøres gruppen på nytt umiddelbart (uten å vente [intervall]) til den er à jour, for å spise unna bulk raskt.
 *   Anbefales sammen med [Kjøremodus.PARALLELT] slik at de serielle gruppene ikke stopper opp mens bulk dreneres.
 */
class TaskGruppe(
    val navn: String,
    val intervall: Duration = GruppertTaskExecutor.STANDARD_JOBB_INTERVALL,
    val tasks: NonEmptyList<suspend (CorrelationId) -> TaskResultat>,
    val kjøremodus: Kjøremodus = Kjøremodus.SERIELT,
    val initialDelay: Duration = GruppertTaskExecutor.STANDARD_INITIAL_DELAY,
    val kjørKontinuerligTilTom: Boolean = false,
)
