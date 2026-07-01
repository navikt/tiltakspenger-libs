package no.nav.tiltakspenger.libs.ktor.common.oppstart

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.jobber.GruppertTaskExecutor
import no.nav.tiltakspenger.libs.jobber.LeaderPodLookup
import no.nav.tiltakspenger.libs.jobber.LeaderPodLookupClient
import no.nav.tiltakspenger.libs.jobber.LeaderPodLookupFeil
import no.nav.tiltakspenger.libs.jobber.RunCheckFactory
import no.nav.tiltakspenger.libs.jobber.TaskGruppe
import java.time.Clock

/**
 * Beskriver en Kafka-consumer som det felles oppstartsmønsteret skal starte ved [ServerReady][io.ktor.server.application.ServerReady] og stoppe rent ved shutdown.
 *
 * Holder kun på navn + start/stopp slik at libs ikke trenger å kjenne til konkrete consumer- eller Kafka-typer (de bor i konsumentene/`libs:kafka`).
 *
 * @param navn Brukes i logging og som navn på [StoppbarBakgrunnsprosess].
 * @param start Den ikke-blokkerende `run()`/`start()` som starter konsument-loopen (typisk på Dispatchers.IO) og returnerer umiddelbart.
 * @param stopp Den blokkerende `stop()` som slutter å polle og venter på at pågående batch behandles/committes.
 * Pakkes i [stoppbarKafkaConsumer] sin to-fase stopp.
 */
class KafkaConsumerOppsett(
    val navn: String,
    val start: () -> Unit,
    val stopp: () -> Unit,
)

/**
 * Bygger en [RunCheckFactory] med standard leader-election-oppsett for skedulerte jobber.
 *
 * I NAIS slås leader election på via [LeaderPodLookupClient] mot leader-elector sidecar ([electorPath]).
 * Lokalt (utenfor NAIS) er det ingen sidecar, så vi later som om vi alltid er leder ([LokalAlltidLeder]) slik at jobbene kan kjøre lokalt.
 * I begge tilfeller styres «kjør kun når appen er klar» av [readiness] (`isReady = readiness::erKlar`).
 *
 * [electorPath] er en lambda slik at den kun evalueres når vi faktisk kjører i NAIS (lokalt finnes ofte ikke `ELECTOR_PATH`).
 */
fun runCheckFactory(
    isNais: Boolean,
    electorPath: () -> String,
    readiness: Readiness,
    logger: KLogger = KotlinLogging.logger { },
): RunCheckFactory = if (isNais) {
    RunCheckFactory(
        leaderPodLookup = LeaderPodLookupClient(electorPath = electorPath(), logger = logger),
        isReady = readiness::erKlar,
    )
} else {
    RunCheckFactory(
        leaderPodLookup = LokalAlltidLeder,
        isReady = readiness::erKlar,
    )
}

/**
 * Leader-election-stub som brukes lokalt (utenfor NAIS) der det ikke finnes en leader-elector sidecar.
 * Lar alltid poden være leder slik at skedulerte jobber kan kjøre på utviklermaskin.
 */
internal object LokalAlltidLeder : LeaderPodLookup {
    override fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean> = true.right()
}

/**
 * Starter de skedulerte task-gruppene ([GruppertTaskExecutor]) og pakker dem som en [StoppbarBakgrunnsprosess] slik at de stoppes rent ved shutdown.
 *
 * Hver [TaskGruppe] kjøres på sitt eget intervall og sin egen [Kjøremodus][no.nav.tiltakspenger.libs.jobber.Kjøremodus].
 * Tasks innad i en gruppe kjøres seriellt, men kun når [runCheckFactory] sine sjekker (readiness + leder) godtar kjøring.
 *
 * @param mdcCallIdKey MDC-nøkkel for correlation id (samme som `callIdMdc(...)` i konsumentens Ktor-oppsett).
 * @param clock Klokka som schedulereren måler intervaller mot; leveres av konsumenten (settes ikke som default her).
 */
fun stoppbarSkedulerteJobber(
    log: KLogger,
    runCheckFactory: RunCheckFactory,
    mdcCallIdKey: String,
    grupper: NonEmptyList<TaskGruppe>,
    clock: Clock,
    navn: String = "skedulerte jobber",
): StoppbarBakgrunnsprosess {
    log.info { "Starter $navn (${grupper.size} gruppe(r))" }
    val taskExecutor = GruppertTaskExecutor.startJob(
        runCheckFactory = runCheckFactory,
        grupper = grupper,
        mdcCallIdKey = mdcCallIdKey,
        clock = clock,
        logger = log,
    )
    log.info { "$navn startet: ${taskExecutor.jobName}" }
    return StoppbarBakgrunnsprosess(navn = "$navn (${taskExecutor.jobName})") { taskExecutor.stop() }
}

/**
 * Setter sammen oppstartsstegene for bakgrunnsprosessene (skedulerte task-grupper + Kafka-consumere) som [startMedOpprydding] kjører i rekkefølge.
 *
 * Hver [Task] blir sin egen serielle [TaskGruppe] (via [Task.tilTaskGruppe]) og slås sammen med [taskGrupper], og kjøres av samme [GruppertTaskExecutor] i ett steg.
 * Hver [KafkaConsumerOppsett] blir et steg som starter consumeren og pakker den i [stoppbarKafkaConsumer].
 * Trukket ut som en egen, ren funksjon slik at sammensetningen kan testes uten å starte en ekte server.
 *
 * @param runCheckFactory Bygges lat ([runCheckFactory]) slik at leader-election/[electorPath] kun hentes ut når det faktisk finnes skedulerte jobber.
 * @param isNais Om appen kjører i NAIS; brukes til å resolve [Task] sine miljøavhengige verdier ([Miljøverdi]).
 */
internal fun bakgrunnsprosessSteg(
    log: KLogger,
    runCheckFactory: () -> RunCheckFactory,
    mdcCallIdKey: String,
    isNais: Boolean,
    clock: Clock,
    tasks: List<Task>,
    taskGrupper: List<TaskGruppe>,
    kafkaConsumers: List<KafkaConsumerOppsett>,
): List<() -> StoppbarBakgrunnsprosess?> = buildList {
    val grupper = (tasks.map { it.tilTaskGruppe(isNais) } + taskGrupper).toNonEmptyListOrNull()
    if (grupper != null) {
        val factory = runCheckFactory()
        add {
            stoppbarSkedulerteJobber(
                log = log,
                runCheckFactory = factory,
                mdcCallIdKey = mdcCallIdKey,
                grupper = grupper,
                clock = clock,
            )
        }
    }
    kafkaConsumers.forEach { consumer ->
        add {
            log.info { "Starter ${consumer.navn}" }
            // start() er ikke-blokkerende; den starter konsument-loopen og returnerer umiddelbart.
            consumer.start()
            log.info { "${consumer.navn} startet" }
            stoppbarKafkaConsumer(log = log, navn = consumer.navn) { consumer.stopp() }
        }
    }
}
