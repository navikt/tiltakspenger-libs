package no.nav.tiltakspenger.libs.jobber

import arrow.core.Either
import arrow.core.NonEmptyList
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.common.withCorrelationIdSuspend
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Kjører flere [TaskGruppe]-r med hvert sitt [TaskGruppe.intervall].
 * Hver gruppe er enten [Kjøremodus.SERIELT] (deler én felles seriell coroutine, serielt med de andre serielle gruppene) eller [Kjøremodus.PARALLELT] (egen coroutine, uavhengig av alle andre grupper).
 *
 * Garantier:
 *  1. To [Kjøremodus.SERIELT]-grupper kjører aldri samtidig.
 *     De drives av én felles seriell coroutine som våkner på neste due-tidspunkt og kjører due grupper én for én.
 *  2. Tasks innad i en gruppe kjøres seriellt i listerekkefølge.
 *     En feil i én task stopper ikke de øvrige (hver wrappes i [Either.catch] og loggføres).
 *  3. Fixed-delay: neste kjøring av en seriell gruppe planlegges [TaskGruppe.intervall] etter at hele kjøringsrunden (alle due grupper) er ferdig; for en parallell gruppe etter at gruppen selv er ferdig.
 *     Serielle grupper med likt intervall holder seg dermed i samme runde, slik at «ingen arbeid»-logging kan samles per runde.
 *  4. Melder en task [TaskResultat.MerArbeid] og [TaskGruppe.kjørKontinuerligTilTom] er satt, kjøres gruppen på nytt umiddelbart til den er à jour, før den faller tilbake til [TaskGruppe.intervall].
 *     For en seriell gruppe drenerer den helt til tom i strekk; de andre serielle gruppene venter så lenge.
 *  5. Ved [stop] kjøres pågående gruppe-syklus ferdig (task-kjøringen er [NonCancellable]); kun ventingen mellom kjøringer avbrytes.
 *  6. Melder samtlige tasks i en kjøringsrunde [TaskResultat.IngenArbeid], logges maks én felles debuglinje for runden i stedet for at hver jobb logger sin egen tomme runde.
 *     En task som feiler (kaster eller melder [TaskResultat.Feilet]) eller melder [TaskResultat.Ferdig]/[TaskResultat.MerArbeid] regnes som at runden hadde arbeid, og da logger ikke executoren noe (jobbene logger selv arbeidet og feilene sine).
 *
 * [runJobCheck] (readiness + leder) evalueres kun når minst én gruppe faktisk har forfalt, slik at vi ikke gjør leder-oppslag på hvert tikk.
 * For de serielle gruppene evalueres [runJobCheck] én gang per oppvåkning, felles for alle due grupper.
 */
class GruppertTaskExecutor private constructor(
    private val log: KLogger,
    private val mdcCallIdKey: String,
    private val runJobCheck: List<RunJobCheck>,
    private val serielleGrupper: List<TaskGruppe>,
    private val parallelleGrupper: List<TaskGruppe>,
    private val clock: Clock,
    private val enableDebuggingLogging: Boolean,
) {

    val jobName: String = "gruppertTaskExecutor"

    private val scope = Skeduleringsscope(jobName, log)

    private fun start() {
        if (serielleGrupper.isNotEmpty()) {
            scope.start { seriellLoop() }
        }
        parallelleGrupper.forEach { gruppe ->
            scope.start { parallellLoop(gruppe) }
        }
    }

    /**
     * Idempotent.
     * Stopper å starte nye kjøringer; pågående gruppe-syklus kjøres ferdig.
     */
    fun stop() = scope.stop()

    /**
     * Én felles coroutine for alle serielle grupper.
     * Våkner på neste due-tidspunkt, evaluerer [runJobCheck] én gang, og kjører due grupper én for én slik at de aldri overlapper hverandre.
     */
    private suspend fun seriellLoop() {
        // Felles utgangspunkt slik at grupper med lik initialDelay blir due i nøyaktig samme runde.
        val start = Instant.now(clock)
        val nesteKjøring = serielleGrupper.associate { it.navn to start.plusMillis(it.initialDelay.inWholeMilliseconds) }.toMutableMap()
        while (currentCoroutineContext().isActive) {
            val ventMs = nesteKjøring.values.min().toEpochMilli() - Instant.now(clock).toEpochMilli()
            if (ventMs > 0) delay(ventMs.milliseconds)

            val nå = Instant.now(clock)
            val due = serielleGrupper.filter { !nesteKjøring.getValue(it.navn).isAfter(nå) }
            runJobCheck.shouldRun().fold(
                ifLeft = { årsak ->
                    if (enableDebuggingLogging) {
                        log.debug { "Serielle grupper hoppes over pga. startkriterier: $årsak" }
                    }
                    val etterRunden = Instant.now(clock)
                    due.forEach { nesteKjøring[it.navn] = nesteKjøringEtter(it, etterRunden) }
                },
                ifRight = {
                    val ingenArbeid = due.map { gruppe -> kjørMedDrenering(gruppe) }.all { it }
                    // Neste kjøring regnes fra slutten av hele runden, slik at grupper med likt intervall holder seg i samme runde.
                    val etterRunden = Instant.now(clock)
                    due.forEach { nesteKjøring[it.navn] = nesteKjøringEtter(it, etterRunden) }
                    if (ingenArbeid && due.isNotEmpty() && enableDebuggingLogging) {
                        log.debug { "Ingen av jobbene hadde arbeid i denne runden: ${due.joinToString { it.navn }}" }
                    }
                },
            )
        }
    }

    /** Egen loop per parallell gruppe; uavhengig av de andre gruppene. */
    private suspend fun parallellLoop(gruppe: TaskGruppe) {
        delay(gruppe.initialDelay)
        while (currentCoroutineContext().isActive) {
            runJobCheck.shouldRun().fold(
                ifLeft = { årsak ->
                    if (enableDebuggingLogging) {
                        log.debug { "Gruppe '${gruppe.navn}' hoppes over pga. startkriterier: $årsak" }
                    }
                },
                ifRight = {
                    val ingenArbeid = kjørMedDrenering(gruppe)
                    if (ingenArbeid && enableDebuggingLogging) {
                        log.debug { "Jobben '${gruppe.navn}' hadde ikke arbeid i denne runden." }
                    }
                },
            )
            delay(gruppe.intervall)
        }
    }

    private fun nesteKjøringEtter(gruppe: TaskGruppe, fra: Instant): Instant =
        fra.plusMillis(gruppe.intervall.inWholeMilliseconds)

    /**
     * Kjører gruppen én gang, og – dersom [TaskGruppe.kjørKontinuerligTilTom] – på nytt til det ikke er mer arbeid.
     * Avbryter dreneringen dersom executoren stoppes ([currentCoroutineContext] ikke lenger aktiv).
     * Returnerer om gruppen var uten arbeid, altså at samtlige tasks i første kjøring meldte [TaskResultat.IngenArbeid].
     */
    private suspend fun kjørMedDrenering(gruppe: TaskGruppe): Boolean {
        var kjøring = kjørTasks(gruppe)
        val ingenArbeid = kjøring.ingenArbeid
        while (gruppe.kjørKontinuerligTilTom && kjøring.merArbeid && currentCoroutineContext().isActive) {
            kjøring = kjørTasks(gruppe)
        }
        return ingenArbeid
    }

    /**
     * Utfallet av én kjøring av en gruppe.
     *
     * @param merArbeid Om minst én task meldte [TaskResultat.MerArbeid].
     * @param ingenArbeid Om samtlige tasks meldte [TaskResultat.IngenArbeid].
     */
    private data class GruppeKjøring(val merArbeid: Boolean, val ingenArbeid: Boolean)

    /**
     * Kjører tasksene i gruppen seriellt og returnerer utfallet som [GruppeKjøring].
     * Task-kjøringen er [NonCancellable] slik at en pågående syklus alltid kjøres ferdig ved shutdown.
     * En task som feiler (kaster eller melder [TaskResultat.Feilet]) regnes ikke som [TaskResultat.IngenArbeid], slik at vi aldri melder «ingen arbeid» for en runde med feil.
     */
    private suspend fun kjørTasks(gruppe: TaskGruppe): GruppeKjøring = withContext(NonCancellable) {
        var merArbeid = false
        var ingenArbeid = true
        withCorrelationIdSuspend(log, mdcCallIdKey) { correlationId ->
            gruppe.tasks.forEach { task ->
                Either.catch {
                    when (task(correlationId)) {
                        TaskResultat.MerArbeid -> {
                            merArbeid = true
                            ingenArbeid = false
                        }

                        TaskResultat.Ferdig, TaskResultat.Feilet -> ingenArbeid = false

                        TaskResultat.IngenArbeid -> Unit
                    }
                }.onLeft { throwable ->
                    ingenArbeid = false
                    log.error(throwable) { "Feil ved kjøring av task i gruppe '${gruppe.navn}'. correlationId: $correlationId" }
                }
            }
        }
        GruppeKjøring(merArbeid = merArbeid, ingenArbeid = ingenArbeid)
    }

    companion object {
        /**
         * Standardintervall mellom kjøringer for en [TaskGruppe] som ikke setter [TaskGruppe.intervall] selv.
         */
        val STANDARD_JOBB_INTERVALL: Duration = 10.seconds

        /**
         * Standard ventetid før første kjøring for en [TaskGruppe] som ikke setter [TaskGruppe.initialDelay] selv.
         */
        val STANDARD_INITIAL_DELAY: Duration = 1.minutes

        fun startJob(
            runCheckFactory: RunCheckFactory,
            grupper: NonEmptyList<TaskGruppe>,
            mdcCallIdKey: String,
            runJobCheck: List<RunJobCheck> = listOf(runCheckFactory.isReady(), runCheckFactory.leaderPod()),
            clock: Clock,
            logger: KLogger = KotlinLogging.logger { },
            enableDebuggingLogging: Boolean = true,
        ): GruppertTaskExecutor {
            require(grupper.map { it.navn }.toSet().size == grupper.size) { "Gruppenavn må være unike." }
            grupper.forEach { gruppe ->
                require(gruppe.intervall.isPositive()) { "Intervall for gruppe '${gruppe.navn}' må være større enn 0." }
                require(!gruppe.initialDelay.isNegative()) { "initialDelay for gruppe '${gruppe.navn}' kan ikke være negativ." }
            }

            val serielle = grupper.filter { it.kjøremodus == Kjøremodus.SERIELT }
            val parallelle = grupper.filter { it.kjøremodus == Kjøremodus.PARALLELT }
            logger.info {
                "Starter GruppertTaskExecutor med ${serielle.size} seriell(e) og ${parallelle.size} parallell(e) gruppe(r)."
            }
            return GruppertTaskExecutor(
                log = logger,
                mdcCallIdKey = mdcCallIdKey,
                runJobCheck = runJobCheck,
                serielleGrupper = serielle,
                parallelleGrupper = parallelle,
                clock = clock,
                enableDebuggingLogging = enableDebuggingLogging,
            ).also { it.start() }
        }
    }
}
