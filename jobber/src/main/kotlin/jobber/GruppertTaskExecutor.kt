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
 *  3. Fixed-delay: neste kjøring av en gruppe planlegges [TaskGruppe.intervall] etter at forrige kjøring er ferdig.
 *  4. Melder en task [TaskResultat.MerArbeid] og [TaskGruppe.kjørKontinuerligTilTom] er satt, kjøres gruppen på nytt umiddelbart til den er à jour, før den faller tilbake til [TaskGruppe.intervall].
 *     For en seriell gruppe drenerer den helt til tom i strekk; de andre serielle gruppene venter så lenge.
 *  5. Ved [stop] kjøres pågående gruppe-syklus ferdig (task-kjøringen er [NonCancellable]); kun ventingen mellom kjøringer avbrytes.
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
            scope.start { serieltLoop() }
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
    private suspend fun serieltLoop() {
        val nesteKjøring = serielleGrupper.associate { it.navn to initialInstant(it) }.toMutableMap()
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
                    due.forEach { nesteKjøring[it.navn] = nesteKjøringEtter(it) }
                },
                ifRight = {
                    due.forEach { gruppe ->
                        kjørMedDrenering(gruppe)
                        nesteKjøring[gruppe.navn] = nesteKjøringEtter(gruppe)
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
                ifRight = { kjørMedDrenering(gruppe) },
            )
            delay(gruppe.intervall)
        }
    }

    private fun initialInstant(gruppe: TaskGruppe): Instant =
        Instant.now(clock).plusMillis(gruppe.initialDelay.inWholeMilliseconds)

    private fun nesteKjøringEtter(gruppe: TaskGruppe): Instant =
        Instant.now(clock).plusMillis(gruppe.intervall.inWholeMilliseconds)

    /**
     * Kjører gruppen én gang, og – dersom [TaskGruppe.kjørKontinuerligTilTom] – på nytt til det ikke er mer arbeid.
     * Avbryter dreneringen dersom executoren stoppes ([currentCoroutineContext] ikke lenger aktiv).
     */
    private suspend fun kjørMedDrenering(gruppe: TaskGruppe) {
        var merArbeid = kjørTasks(gruppe)
        while (gruppe.kjørKontinuerligTilTom && merArbeid && currentCoroutineContext().isActive) {
            merArbeid = kjørTasks(gruppe)
        }
    }

    /**
     * Kjører tasksene i gruppen seriellt og returnerer om noen meldte [TaskResultat.MerArbeid].
     * Task-kjøringen er [NonCancellable] slik at en pågående syklus alltid kjøres ferdig ved shutdown.
     */
    private suspend fun kjørTasks(gruppe: TaskGruppe): Boolean = withContext(NonCancellable) {
        var merArbeid = false
        withCorrelationIdSuspend(log, mdcCallIdKey) { correlationId ->
            gruppe.tasks.forEach { task ->
                Either.catch {
                    if (task(correlationId) == TaskResultat.MerArbeid) merArbeid = true
                }.onLeft { throwable ->
                    log.error(throwable) { "Feil ved kjøring av task i gruppe '${gruppe.navn}'. correlationId: $correlationId" }
                }
            }
        }
        merArbeid
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
