package no.nav.tiltakspenger.libs.jobber

import arrow.core.NonEmptyList
import no.nav.tiltakspenger.libs.common.CorrelationId
import kotlin.time.Duration

/**
 * Hvordan en [TaskGruppe] forholder seg til de andre gruppene i en [GruppertTaskExecutor].
 *
 * Merk: dette styrer forholdet *mellom* grupper.
 * Tasks *innad* i en gruppe kjøres alltid serielt i listerekkefølge (neste task starter først når forrige er ferdig), slik at vi ikke overbelaster databasen med samtidige spørringer.
 */
enum class Kjøremodus {
    /**
     * Gruppen deler den ene serielle scheduler-coroutinen med alle andre [SERIELT]-grupper og kjøres etter tur med dem.
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
 * Resultatet en task returnerer for å styre kontinuerlig kjøring ([TaskGruppe.kjørKontinuerligTilTom]) og samlet «ingen arbeid»-logging.
 *
 * Tasks som ikke bryr seg om noen av delene returnerer [Ferdig].
 */
sealed interface TaskResultat {
    /**
     * Det gjenstår mer arbeid akkurat nå (f.eks. en side-/limit-basert henting fikk fullt resultatsett).
     * I en gruppe med [TaskGruppe.kjørKontinuerligTilTom] fører dette til at gruppen kjøres på nytt umiddelbart.
     */
    data object MerArbeid : TaskResultat

    /** Utførte arbeid og er à jour – gruppen kan vente til neste intervall. */
    data object Ferdig : TaskResultat

    /**
     * Tasken fant ikke noe arbeid (typisk en polling-jobb som fant 0 rader).
     * Brukes av [GruppertTaskExecutor] til å samle «ingen arbeid»-logging til maks én debuglinje per kjøringsrunde, i stedet for at hver jobb logger sin egen tomme runde.
     * Meld [Ferdig] dersom tasken faktisk utførte arbeid.
     */
    data object IngenArbeid : TaskResultat

    /**
     * Tasken forsøkte å gjøre arbeid, men feilet, og har selv logget feilen.
     * Regnes som at runden hadde arbeid, slik at det aldri logges «ingen arbeid» for en runde med feil.
     * Tasks som heller lar exceptions boble opp får samme behandling ([GruppertTaskExecutor] fanger og logger dem selv).
     */
    data object Feilet : TaskResultat
}

/**
 * Slår sammen resultatene fra flere del-jobber til ett [TaskResultat] for tasken som helhet.
 * [TaskResultat.MerArbeid] vinner over [TaskResultat.Feilet], som vinner over [TaskResultat.Ferdig], som vinner over [TaskResultat.IngenArbeid].
 * En tom samling regnes som [TaskResultat.IngenArbeid].
 */
fun Iterable<TaskResultat>.tilSamletResultat(): TaskResultat = when {
    any { it == TaskResultat.MerArbeid } -> TaskResultat.MerArbeid
    any { it == TaskResultat.Feilet } -> TaskResultat.Feilet
    any { it == TaskResultat.Ferdig } -> TaskResultat.Ferdig
    else -> TaskResultat.IngenArbeid
}

/**
 * En navngitt gruppe av tasks som kjøres på et eget [intervall] av en [GruppertTaskExecutor].
 *
 * @param navn Unikt navn i executoren; brukes i logging.
 * @param intervall Ventetid mellom kjøringer, målt fra *etter* at forrige kjøring av gruppen er ferdig (fixed-delay).
 *   Default [GruppertTaskExecutor.STANDARD_JOBB_INTERVALL].
 * @param tasks Tasks som kjøres serielt i listerekkefølge hver gang gruppen kjører.
 *   En feil i én task stopper ikke de øvrige.
 *   Returner [TaskResultat.IngenArbeid] når det ikke fantes noe å gjøre, [TaskResultat.Ferdig] når du har utført arbeid og er à jour, eller [TaskResultat.MerArbeid] for å utnytte [kjørKontinuerligTilTom].
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
