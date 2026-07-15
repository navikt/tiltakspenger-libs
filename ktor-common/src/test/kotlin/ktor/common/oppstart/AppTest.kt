package no.nav.tiltakspenger.libs.ktor.common.oppstart

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.ServerReady
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.jobber.JobbSkalIkkeKjøre
import no.nav.tiltakspenger.libs.jobber.TaskGruppe
import no.nav.tiltakspenger.libs.jobber.TaskResultat
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Verifiserer det høynivå oppstarts-mønsteret som tar inn jobber + Kafka: [runCheckFactory], [stoppbarSkedulerteJobber], [bakgrunnsprosessSteg], [konfigurerOppstart] og [startApp].
 *
 * Den underliggende orkestreringen (idempotent start/stopp, shutdown-race, to-fase Kafka-stopp) testes i [OppstartTest]; her verifiserer vi sammensetningen av jobber/Kafka og at fasaden wirer alt riktig.
 */
class AppTest {
    private val log = KotlinLogging.logger { }

    private val ventetimeoutMs = 2_000L

    private val clock = Clock.systemUTC()

    /** Tom skedulert jobb som aldri gjør noe; vi tester oppkobling/livssyklus, ikke selve jobben. */
    private val tomTask: suspend (CorrelationId) -> TaskResultat = { TaskResultat.Ferdig }

    @Test
    fun `runCheckFactory lokalt later som leder og respekterer readiness`() {
        val readiness = Readiness()
        // Utelater logger for å dekke default-loggeren.
        val factory = runCheckFactory(isNais = false, electorPath = { error("electorPath skal ikke leses lokalt") }, readiness = readiness, clock = clock)

        // LokalAlltidLeder lar oss alltid være leder lokalt.
        factory.leaderPod().shouldRun() shouldBe Unit.right()

        // isReady følger readiness-flagget.
        factory.isReady().shouldRun() shouldBe JobbSkalIkkeKjøre.IkkeKlar.left()
        readiness.settKlar()
        factory.isReady().shouldRun() shouldBe Unit.right()
    }

    @Test
    fun `runCheckFactory i NAIS bygger leader-elector-klient mot electorPath`() {
        var electorPathLest = false
        val factory = runCheckFactory(
            isNais = true,
            electorPath = {
                electorPathLest = true
                "http://localhost:1/"
            },
            readiness = Readiness(),
            clock = clock,
            logger = log,
        )

        // electorPath skal være lest når vi kjører i NAIS (LeaderPodLookupClient bygges).
        electorPathLest shouldBe true
        // Vi kaller ikke leaderPod().shouldRun() her (det ville gjort et nettverkskall); readiness-sjekken holder.
        factory.isReady().shouldRun() shouldBe JobbSkalIkkeKjøre.IkkeKlar.left()
    }

    @Test
    fun `stoppbarSkedulerteJobber starter og stopper GruppertTaskExecutor`() {
        // Utelater navn for å dekke default-verdien.
        val prosess = stoppbarSkedulerteJobber(
            log = log,
            runCheckFactory = runCheckFactory(isNais = false, electorPath = { error("ikke lokalt") }, readiness = Readiness(), clock = clock),
            mdcCallIdKey = "call-id",
            grupper = nonEmptyListOf(TaskGruppe(navn = "test", intervall = 1.seconds, tasks = nonEmptyListOf(tomTask))),
            clock = clock,
        )

        prosess.navn.startsWith("skedulerte jobber") shouldBe true
        // Skal kunne stoppes rent (kansellerer den underliggende coroutine-scopen).
        prosess.stopp()
    }

    @Test
    fun `bakgrunnsprosessSteg uten jobber og consumere gir ingen steg og bygger ikke runCheckFactory`() {
        var factoryBygget = false
        val steg = bakgrunnsprosessSteg(
            log = log,
            runCheckFactory = {
                factoryBygget = true
                runCheckFactory(isNais = false, electorPath = { error("ikke lokalt") }, readiness = Readiness(), clock = clock)
            },
            mdcCallIdKey = "call-id",
            isNais = false,
            clock = clock,
            tasks = emptyList(),
            taskGrupper = emptyList(),
            kafkaConsumers = emptyList(),
        )

        steg.isEmpty() shouldBe true
        // Uten jobber skal vi ikke bygge runCheckFactory (og dermed ikke hente ut electorPath/leader-election).
        factoryBygget shouldBe false
    }

    @Test
    fun `bakgrunnsprosessSteg med kun Kafka-consumere gir steg uten å bygge runCheckFactory`() {
        var factoryBygget = false
        val steg = bakgrunnsprosessSteg(
            log = log,
            runCheckFactory = {
                factoryBygget = true
                runCheckFactory(isNais = false, electorPath = { error("ikke lokalt") }, readiness = Readiness(), clock = clock)
            },
            mdcCallIdKey = "call-id",
            isNais = false,
            clock = clock,
            tasks = emptyList(),
            taskGrupper = emptyList(),
            kafkaConsumers = listOf(KafkaConsumerOppsett(navn = "kun-kafka", start = {}, stopp = {})),
        )

        steg.size shouldBe 1
        // Leader-election gjelder kun skedulerte jobber; med kun Kafka skal runCheckFactory ikke bygges.
        factoryBygget shouldBe false
        steg.first()()?.stopp()
    }

    @Test
    fun `bakgrunnsprosessSteg med eksplisitte taskGrupper gir ett skedulert steg`() {
        val steg = bakgrunnsprosessSteg(
            log = log,
            runCheckFactory = { runCheckFactory(isNais = false, electorPath = { error("ikke lokalt") }, readiness = Readiness(), clock = clock) },
            mdcCallIdKey = "call-id",
            isNais = false,
            clock = clock,
            tasks = emptyList(),
            taskGrupper = listOf(TaskGruppe(navn = "egen", intervall = 1.seconds, tasks = nonEmptyListOf(tomTask))),
            kafkaConsumers = emptyList(),
        )

        steg.size shouldBe 1
        // Kjør steget for å dekke oppstart + rydd opp igjen.
        steg.first()()?.stopp()
    }

    @Test
    fun `bakgrunnsprosessSteg bygger én seriell gruppe per Task med miljøavhengige verdier`() {
        val steg = bakgrunnsprosessSteg(
            log = log,
            runCheckFactory = { runCheckFactory(isNais = false, electorPath = { error("ikke lokalt") }, readiness = Readiness(), clock = clock) },
            mdcCallIdKey = "call-id",
            isNais = false,
            clock = clock,
            tasks = listOf(
                Task(
                    navn = "rask-lokalt",
                    intervall = Miljøverdi.ulik(nais = 1.minutes, lokal = 1.seconds),
                    initialDelay = Miljøverdi.lik(1.seconds),
                    utfør = tomTask,
                ),
            ),
            taskGrupper = emptyList(),
            kafkaConsumers = emptyList(),
        )

        steg.size shouldBe 1
        steg.first()()?.stopp()
    }

    @Test
    fun `konfigurerOppstart starter jobber og Kafka ved ServerReady og stopper dem ved shutdown`() = testApplication {
        val kafkaStartet = AtomicBoolean(false)
        val kafkaStoppet = AtomicBoolean(false)
        lateinit var app: Application
        application {
            app = this
            val readiness = Readiness()
            routing { healthRoutes(readiness::erKlar) }
            // Utelater shutdownPågår for å dekke default-verdien.
            konfigurerOppstart(
                log = log,
                isNais = false,
                readiness = readiness,
                oppsett = Bakgrunnsprosessoppsett(
                    mdcCallIdKey = "call-id",
                    clock = clock,
                    electorPath = { error("electorPath skal ikke leses lokalt") },
                    tasks = listOf(Task(navn = "tom", utfør = tomTask)),
                    kafkaConsumers = listOf(
                        KafkaConsumerOppsett(
                            navn = "test-consumer",
                            start = { kafkaStartet.set(true) },
                            stopp = { kafkaStoppet.set(true) },
                        ),
                    ),
                ),
            )
        }

        client.get("/isready").status shouldBe HttpStatusCode.ServiceUnavailable

        app.monitor.raise(ServerReady, app.environment)

        client.get("/isready").status shouldBe HttpStatusCode.OK
        kafkaStartet.get() shouldBe true

        app.monitor.raise(ApplicationStopping, app)

        client.get("/isready").status shouldBe HttpStatusCode.ServiceUnavailable
        kafkaStoppet.get() shouldBe true
    }

    @Test
    fun `konfigurerOppstart uten jobber og Kafka markerer fortsatt appen klar ved ServerReady`() = testApplication {
        lateinit var app: Application
        application {
            app = this
            val readiness = Readiness()
            routing { healthRoutes(readiness::erKlar) }
            // Utelater tasks og kafkaConsumers for å dekke default-verdiene.
            konfigurerOppstart(
                log = log,
                isNais = false,
                readiness = readiness,
                oppsett = Bakgrunnsprosessoppsett(mdcCallIdKey = "call-id", clock = clock, electorPath = { error("electorPath skal ikke leses lokalt") }),
            )
        }

        client.get("/isready").status shouldBe HttpStatusCode.ServiceUnavailable
        app.monitor.raise(ServerReady, app.environment)
        client.get("/isready").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `konfigurerOppstart uten oppsett i det hele tatt markerer fortsatt appen klar ved ServerReady`() = testApplication {
        lateinit var app: Application
        application {
            app = this
            val readiness = Readiness()
            routing { healthRoutes(readiness::erKlar) }
            // Utelater oppsett helt (null) for en app uten bakgrunnsprosesser – da trengs verken electorPath eller clock.
            konfigurerOppstart(
                log = log,
                isNais = false,
                readiness = readiness,
            )
        }

        client.get("/isready").status shouldBe HttpStatusCode.ServiceUnavailable
        app.monitor.raise(ServerReady, app.environment)
        client.get("/isready").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `startApp starter en ekte app og stopper rent`() {
        val port = ServerSocket(0).use { it.localPort }
        val serverKlar = CountDownLatch(1)
        val appRef = AtomicReference<Application?>(null)

        val serverTråd = Thread {
            // Utelater oppsett, readiness og shutdown*-parametrene for å dekke default-verdiene (app uten bakgrunnsprosesser).
            startApp(
                log = log,
                port = port,
                isNais = false,
            ) { readiness ->
                appRef.set(this)
                routing { healthRoutes(readiness::erKlar) }
                monitor.subscribe(ServerReady) { serverKlar.countDown() }
            }
        }
        serverTråd.isDaemon = true
        serverTråd.start()

        try {
            serverKlar.await(ventetimeoutMs, TimeUnit.MILLISECONDS) shouldBe true

            val klient = HttpClient.newHttpClient()
            // Bakgrunnsprosessene startes på ServerReady (samme event som latchen over), så /isready blir READY.
            val svar = klient.send(
                HttpRequest.newBuilder(URI("http://localhost:$port/isready")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            svar.statusCode() shouldBe 200
            svar.body() shouldBe "READY"
        } finally {
            // Stopper den ekte serveren slik at startApp returnerer (server.start(wait = true)) og tråden avsluttes.
            appRef.get()?.engine?.stop(0, 500)
            serverTråd.join(ventetimeoutMs)
        }
    }
}
