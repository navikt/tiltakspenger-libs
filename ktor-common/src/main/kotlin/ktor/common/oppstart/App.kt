package no.nav.tiltakspenger.libs.ktor.common.oppstart

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.application.Application
import no.nav.tiltakspenger.libs.jobber.TaskGruppe
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Samler *hva* appen skal kjøre av bakgrunnsprosesser + det tilhørende leader-election-/scheduling-oppsettet, slik at [startApp] og [konfigurerOppstart] slipper å duplisere den samme lange parameterlisten.
 *
 * Merk den bevisste asymmetrien mellom [tasks]/[taskGrupper] og [kafkaConsumers] – den speiler hvordan de faktisk kjøres:
 *  - [tasks] er selvstendige, navngitte skedulerte jobber (én [Task] = én seriell gruppe), med hvert sitt intervall/initialDelay (miljøavhengig via [Miljøverdi]).
 *  - [taskGrupper] er for det avanserte tilfellet: flere lambdaer batchet i én gruppe, parallell [Kjøremodus][no.nav.tiltakspenger.libs.jobber.Kjøremodus] og/eller kontinuerlig drenering.
 *  - [kafkaConsumers] er *N* selvstendige consumer-livssykluser som startes/stoppes hver for seg, derfor navngitte [KafkaConsumerOppsett].
 *
 * [tasks] og [taskGrupper] kan kombineres og kjøres på samme [GruppertTaskExecutor][no.nav.tiltakspenger.libs.jobber.GruppertTaskExecutor].
 *
 * @param mdcCallIdKey MDC-nøkkel for correlation id (samme som `callIdMdc(...)` i konsumentens Ktor-oppsett).
 * @param electorPath Sti til leader-elector-sidecaren.
 *   Kun lest i NAIS; lokalt/test blir lambdaen aldri evaluert, men den må sendes inn (bruk gjerne en guard som feiler hvis den kalles lokalt).
 * @param tasks Skedulerte jobber; hver [Task] kjøres seriellt på sitt eget intervall (tom liste = ingen).
 * @param taskGrupper Eksplisitte task-grupper for finkornet styring (batching/parallell/drenering) (tom liste = ingen).
 * @param kafkaConsumers Kafka-consumere som startes ved ServerReady og stoppes rent ved shutdown (tom liste = ingen Kafka).
 * @param clock Klokka schedulereren måler intervaller mot.
 *   Leveres av konsumenten (typisk appens egen `Clock`), slik at libs ikke defaulter klokka i prod.
 */
class Bakgrunnsprosessoppsett(
    val mdcCallIdKey: String,
    val electorPath: () -> String,
    val tasks: List<Task> = emptyList(),
    val taskGrupper: List<TaskGruppe> = emptyList(),
    val kafkaConsumers: List<KafkaConsumerOppsett> = emptyList(),
    val clock: Clock,
)

/**
 * Kobler hele bakgrunns-livssyklusen (leader-election + skedulerte jobber + Kafka) opp mot den felles orkestreringen ([konfigurerLivssyklus]).
 *
 * Trukket ut fra [startApp] som en [Application]-extension uten server-bootstrap slik at konsumentene kan kjøre nøyaktig den samme oppkoblingen i `testApplication { application { ... } }` (uten en ekte Netty-server).
 * Derfor tar den også inn [readiness] eksplisitt (tester injiserer sin egen), mens [startApp] lager den internt.
 *
 * Bygger [RunCheckFactory][no.nav.tiltakspenger.libs.jobber.RunCheckFactory] via [runCheckFactory], setter sammen stegene via [bakgrunnsprosessSteg], og lar [startMedOpprydding] rydde opp dersom et startsteg kaster.
 *
 * @param oppsett Hva som skal kjøres (jobber + Kafka) og leader-election-/scheduling-oppsettet, se [Bakgrunnsprosessoppsett].
 *   `null` (default) betyr at appen ikke har noen bakgrunnsprosesser – da slipper konsumenten å konstruere et oppsett med `electorPath`/`clock`.
 * @param shutdownPågår Deles med server-bootstrap slik at livssyklusen og [startKtorServer] ser samme shutdown-tilstand.
 */
fun Application.konfigurerOppstart(
    log: KLogger,
    isNais: Boolean,
    readiness: Readiness,
    oppsett: Bakgrunnsprosessoppsett? = null,
    shutdownPågår: AtomicBoolean = AtomicBoolean(false),
) {
    konfigurerLivssyklus(
        log = log,
        readiness = readiness,
        shutdownPågår = shutdownPågår,
        startBakgrunnsprosesser = {
            if (oppsett == null) {
                emptyList()
            } else {
                startMedOpprydding(
                    log = log,
                    startSteg = bakgrunnsprosessSteg(
                        log = log,
                        // Bygges lat slik at electorPath/leader-election kun hentes ut når det faktisk finnes skedulerte jobber.
                        runCheckFactory = {
                            runCheckFactory(
                                isNais = isNais,
                                electorPath = oppsett.electorPath,
                                readiness = readiness,
                                clock = oppsett.clock,
                                logger = log,
                            )
                        },
                        mdcCallIdKey = oppsett.mdcCallIdKey,
                        isNais = isNais,
                        clock = oppsett.clock,
                        tasks = oppsett.tasks,
                        taskGrupper = oppsett.taskGrupper,
                        kafkaConsumers = oppsett.kafkaConsumers,
                    ),
                )
            }
        },
    )
}

/**
 * Felles inngangspunkt for å starte en ktor-app med standardisert oppstart: én funksjon som tar inn både de skedulerte jobbene og Kafka-consumerene via [Bakgrunnsprosessoppsett].
 *
 * Bygger på to lag som også kan brukes hver for seg:
 *  - runtime/transport: [startKtorServer] (Netty, graceful shutdown, SIGTERM-under-oppstart-racet),
 *  - livssyklus + bakgrunnsprosesser: [konfigurerOppstart] ([konfigurerLivssyklus] + leader-election + jobber + Kafka).
 *
 * Konsumenten trenger kun å sette opp sitt eget Ktor-oppsett (plugins/auth/routes inkl. [healthRoutes]) i [ktorModule] og levere sine jobber/consumere.
 * [Readiness] lages internt her og deles automatisk: [ktorModule] får den inn slik at `healthRoutes(readiness::erKlar)` og livssyklusen ser samme tilstand.
 *
 * Eksempel (i konsumentens `start()`):
 * ```
 * startApp(
 *     log = log,
 *     port = httpPort(),
 *     isNais = isNais,
 *     oppsett = Bakgrunnsprosessoppsett(
 *         mdcCallIdKey = CALL_ID_MDC_KEY,
 *         electorPath = Configuration::electorPath,
 *         tasks = listOf(Task(navn = "gjør-noe", utfør = { ctx.someService.gjørNoe(); TaskResultat.Ferdig })),
 *         kafkaConsumers = if (isNais) listOf(KafkaConsumerOppsett("min-consumer", { ctx.consumer.run() }, { ctx.consumer.stop() })) else emptyList(),
 *     ),
 * ) { readiness ->
 *     ktorSetup(applicationContext = ctx, readiness = readiness)
 * }
 * ```
 *
 * @param host Nettverksgrensesnittet serveren binder til, se [startKtorServer].
 *   Lokale kjøringer bør sende `127.0.0.1`; default `0.0.0.0` er nødvendig i Nais og containere.
 * @param oppsett Hva som skal kjøres (jobber + Kafka) og leader-election-/scheduling-oppsettet, se [Bakgrunnsprosessoppsett].
 *   `null` (default) betyr at appen ikke har noen bakgrunnsprosesser (kun HTTP), og da trengs verken `electorPath` eller `clock`.
 * @param ktorModule Konsumentens eget Ktor-oppsett.
 *   Får [Readiness] slik at den kan registrere `healthRoutes(readiness::erKlar)`.
 */
fun startApp(
    log: KLogger,
    port: Int,
    host: String = "0.0.0.0",
    isNais: Boolean,
    oppsett: Bakgrunnsprosessoppsett? = null,
    shutdownGracePeriodMillis: Long = 5_000,
    shutdownTimeoutMillis: Long = 30_000,
    ktorModule: Application.(readiness: Readiness) -> Unit,
) {
    val readiness = Readiness()
    startKtorServer(
        log = log,
        port = port,
        host = host,
        shutdownGracePeriodMillis = shutdownGracePeriodMillis,
        shutdownTimeoutMillis = shutdownTimeoutMillis,
    ) { shutdownPågår ->
        konfigurerOppstart(
            log = log,
            isNais = isNais,
            readiness = readiness,
            oppsett = oppsett,
            shutdownPågår = shutdownPågår,
        )
        ktorModule(readiness)
    }
}
