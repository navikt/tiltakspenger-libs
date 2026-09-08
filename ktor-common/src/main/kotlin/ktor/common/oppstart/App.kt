package no.nav.tiltakspenger.libs.ktor.common.oppstart

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.application.Application
import io.micrometer.core.instrument.MeterRegistry
import no.nav.tiltakspenger.libs.jobber.TaskGruppe
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Samler de skedulerte jobbene og det de trenger for å kjøre: leader election, MDC-nøkkel, klokka schedulereren måler mot og registeret schedulereren fører målingene i.
 *
 * De fire feltene hører til jobbene og ingenting annet: [electorPath] og [clock] brukes av leader-election-oppsettet ([runCheckFactory]), mens [mdcCallIdKey], [clock] og [meterRegistry] brukes av schedulereren ([stoppbarSkedulerteJobber]).
 * Registeret hører til jobbene på samme måte som klokka, siden det er schedulereren som fører målingene.
 * Derfor bor de her og ikke på [Bakgrunnsprosessoppsett] – en app som kun har Kafka-consumere skal hverken sette opp leader election eller finne på en `ELECTOR_PATH`.
 *
 * Merk den bevisste asymmetrien mellom [tasks] og [taskGrupper] – den speiler hvordan de faktisk kjøres:
 *  - [tasks] er selvstendige, navngitte skedulerte jobber (én [Task] = én seriell gruppe), med hvert sitt intervall/initialDelay (miljøavhengig via [Miljøverdi]).
 *  - [taskGrupper] er for det avanserte tilfellet: flere lambdaer batchet i én gruppe, parallell [Kjøremodus][no.nav.tiltakspenger.libs.jobber.Kjøremodus] og/eller kontinuerlig drenering.
 *
 * [tasks] og [taskGrupper] kan kombineres og kjøres på samme [GruppertTaskExecutor][no.nav.tiltakspenger.libs.jobber.GruppertTaskExecutor].
 * Begge kan være tomme samtidig – da bygges ingen steg, og [electorPath] leses aldri.
 * Det er tilsiktet, siden konsumentene ofte bygger listene miljøavhengig (`tasks = if (isNais) ... else emptyList()`).
 *
 * @param mdcCallIdKey MDC-nøkkel for correlation id (samme som `callIdMdc(...)` i konsumentens Ktor-oppsett).
 * @param electorPath Sti til leader-elector-sidecaren.
 *   Kun lest i NAIS; lokalt/test blir lambdaen aldri evaluert, men den må sendes inn (bruk gjerne en guard som feiler hvis den kalles lokalt).
 * @param clock Klokka schedulereren måler intervaller mot.
 *   Leveres av konsumenten (typisk appens egen `Clock`), slik at libs ikke defaulter klokka i prod.
 * @param meterRegistry Registeret som mottar målinger fra de skedulerte jobbene.
 * @param tasks Skedulerte jobber; hver [Task] kjøres seriellt på sitt eget intervall (tom liste = ingen).
 * @param taskGrupper Eksplisitte task-grupper for finkornet styring (batching/parallell/drenering) (tom liste = ingen).
 */
class Jobboppsett(
    val mdcCallIdKey: String,
    val electorPath: () -> String,
    val clock: Clock,
    val meterRegistry: MeterRegistry,
    val tasks: List<Task> = emptyList(),
    val taskGrupper: List<TaskGruppe> = emptyList(),
)

/**
 * Samler *hva* appen skal kjøre av bakgrunnsprosesser, slik at [startApp] og [konfigurerOppstart] slipper å duplisere den samme lange parameterlisten.
 *
 * De to feltene er skilt fordi de to slags bakgrunnsprosessene trenger ulike ting:
 *  - [jobber] er skedulerte jobber, som bare kan kjøre på lederpoden og derfor drar med seg leader election, MDC-nøkkel og klokke (se [Jobboppsett]).
 *  - [kafkaConsumers] er *N* selvstendige consumer-livssykluser som startes/stoppes hver for seg, derfor navngitte [KafkaConsumerOppsett].
 *    De kjører på alle poder og trenger ingen av delene.
 *
 * En app med kun Kafka lar derfor [jobber] stå som `null`, og slipper både `electorPath` og en ubrukt MDC-nøkkel.
 *
 * @param jobber Skedulerte jobber med sitt leader-election-oppsett.
 *   `null` (default) betyr ingen skedulerte jobber – da bygges hverken [RunCheckFactory][no.nav.tiltakspenger.libs.jobber.RunCheckFactory] eller leader-election.
 * @param kafkaConsumers Kafka-consumere som startes ved ServerReady og stoppes rent ved shutdown (tom liste = ingen Kafka).
 */
class Bakgrunnsprosessoppsett(
    val jobber: Jobboppsett? = null,
    val kafkaConsumers: List<KafkaConsumerOppsett> = emptyList(),
)

/**
 * Kobler hele bakgrunns-livssyklusen (leader-election + skedulerte jobber + Kafka) opp mot den felles orkestreringen ([konfigurerLivssyklus]).
 *
 * Trukket ut fra [startApp] som en [Application]-extension uten server-bootstrap slik at konsumentene kan kjøre nøyaktig den samme oppkoblingen i `testApplication { application { ... } }` (uten en ekte Netty-server).
 * Derfor tar den også inn [readiness] eksplisitt (tester injiserer sin egen), mens [startApp] lager den internt.
 *
 * Bygger [RunCheckFactory][no.nav.tiltakspenger.libs.jobber.RunCheckFactory] via [runCheckFactory], setter sammen stegene via [bakgrunnsprosessSteg], og lar [startMedOpprydding] rydde opp dersom et startsteg kaster.
 * Leader-election-oppsettet bygges fra [Jobboppsett], og kun når det finnes skedulerte jobber å kjøre.
 * Uten jobber leses aldri `electorPath`, heller ikke i NAIS.
 *
 * @param oppsett Hva som skal kjøres, se [Bakgrunnsprosessoppsett].
 *   `null` (default) betyr at appen ikke har noen bakgrunnsprosesser – da slipper konsumenten å konstruere et oppsett i det hele tatt.
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
                        runCheckFactory = { jobber ->
                            runCheckFactory(
                                isNais = isNais,
                                electorPath = jobber.electorPath,
                                readiness = readiness,
                                clock = jobber.clock,
                                logger = log,
                            )
                        },
                        isNais = isNais,
                        jobber = oppsett.jobber,
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
 *         jobber = Jobboppsett(
 *             mdcCallIdKey = CALL_ID_MDC_KEY,
 *             electorPath = Configuration::electorPath,
 *             clock = ctx.clock,
 *             meterRegistry = ctx.meterRegistry,
 *             tasks = listOf(Task(navn = "gjør-noe", utfør = { ctx.someService.gjørNoe(); TaskResultat.Ferdig })),
 *         ),
 *         kafkaConsumers = if (isNais) listOf(KafkaConsumerOppsett("min-consumer", { ctx.consumer.run() }, { ctx.consumer.stop() })) else emptyList(),
 *     ),
 * ) { readiness ->
 *     ktorSetup(applicationContext = ctx, readiness = readiness)
 * }
 * ```
 *
 * En app uten skedulerte jobber utelater `jobber` og sender kun `kafkaConsumers`; da trengs verken `electorPath`, MDC-nøkkel, klokke eller register.
 *
 * @param host Nettverksgrensesnittet serveren binder til, se [startKtorServer].
 *   Lokale kjøringer bør sende `127.0.0.1`; default `0.0.0.0` er nødvendig i Nais og containere.
 * @param oppsett Hva som skal kjøres, se [Bakgrunnsprosessoppsett].
 *   `null` (default) betyr at appen ikke har noen bakgrunnsprosesser (kun HTTP).
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
