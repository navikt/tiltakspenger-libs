package no.nav.tiltakspenger.libs.ktor.common.oppstart

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.ServerReady
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kobler opp livssyklusen til appen:
 *  - markerer appen som ikke-klar med en gang og ved shutdown ([markerSomIkkeKlarVedShutdown]),
 *  - starter bakgrunnsprosesser (skedulerte jobber + Kafka) når Netty er ferdig å binde ([ServerReady]),
 *  - stopper dem rent ved shutdown ([ApplicationStopping]).
 *
 * Selve bakgrunnsprosessene injiseres via [startBakgrunnsprosesser] slik at hvert repo kobler på sine egne jobber/consumere, mens orkestreringen (rekkefølge, idempotens, shutdown-race) er felles.
 * At den er injiserbar gjør også at tester kan verifisere orkestreringen uten å starte ekte jobber/Kafka.
 */
fun Application.konfigurerLivssyklus(
    log: KLogger,
    readiness: Readiness,
    shutdownPågår: AtomicBoolean = AtomicBoolean(false),
    startBakgrunnsprosesser: () -> List<StoppbarBakgrunnsprosess>,
) {
    markerSomIkkeKlarVedShutdown(log = log, readiness = readiness, shutdownPågår = shutdownPågår)

    val livssyklus = Bakgrunnsprosesslivssyklus(
        log = log,
        readiness = readiness,
        shutdownPågår = shutdownPågår,
        startBakgrunnsprosesser = startBakgrunnsprosesser,
    )

    monitor.subscribe(ServerReady) {
        // ServerReady fyres av Netty etter at bind() er ferdig.
        // Vi venter på denne tilstanden før appen markeres som klar og før bakgrunnsjobber/Kafka starter, slik at vi ikke prosesserer arbeid mens HTTP-serveren fortsatt er i startup.
        // Se Ktor lifecycle-events: https://ktor.io/docs/server-events.html
        // Se også Ktor NettyApplicationEngine.start: https://github.com/ktorio/ktor/blob/3.4.3/ktor-server/ktor-server-netty/jvm/src/io/ktor/server/netty/NettyApplicationEngine.kt
        livssyklus.startVedServerReady()
    }

    monitor.subscribe(ApplicationStopPreparing) {
        // ApplicationStopPreparing er det første shutdown-signalet (før HTTP-grace-perioden).
        // Vi signaliserer stopp til bakgrunnsprosessene her slik at f.eks. Kafka slutter å plukke nye records med en gang, mens den blokkerende ventingen joines inn ved ApplicationStopping.
        livssyklus.påbegyntStoppVedShutdown()
    }

    monitor.subscribe(ApplicationStopping) {
        livssyklus.stopp()
    }
}

private fun Application.markerSomIkkeKlarVedShutdown(log: KLogger, readiness: Readiness, shutdownPågår: AtomicBoolean) {
    readiness.settIkkeKlar()

    monitor.subscribe(ApplicationStopPreparing) {
        log.info { "ApplicationStopPreparing mottatt - markerer appen som ikke klar" }
        shutdownPågår.set(true)
        readiness.settIkkeKlar()
    }

    monitor.subscribe(ApplicationStopping) {
        log.info { "ApplicationStopping mottatt - markerer appen som ikke klar" }
        // ApplicationStopPreparing er ikke garantert å ha blitt fyrt (f.eks. ved enkelte brå shutdowns), så vi setter flagget her også.
        // Idempotent: gjentatt set(true) er ufarlig, og sikrer at appen aldri står igjen som klar.
        shutdownPågår.set(true)
        readiness.settIkkeKlar()
    }
}

/**
 * Starter stegene i rekkefølge og samler [StoppbarBakgrunnsprosess]-ene (steg som returnerer null hoppes over).
 * Hvis et steg kaster, stoppes de allerede startede prosessene før unntaket kastes videre, slik at vi ikke etterlater delvis startede prosesser uten en vei til å stoppe dem.
 *
 * Praktisk å bruke fra konsumentens [startBakgrunnsprosesser]-lambda.
 */
fun startMedOpprydding(
    log: KLogger,
    startSteg: List<() -> StoppbarBakgrunnsprosess?>,
): List<StoppbarBakgrunnsprosess> {
    val startede = mutableListOf<StoppbarBakgrunnsprosess>()
    // Either.catch re-kaster fatale throwables (via nonFatalOrThrow), så vi rydder kun opp ved ikke-fatale feil.
    Either.catch {
        startSteg.forEach { steg -> steg()?.let { startede.add(it) } }
    }.onLeft { feil ->
        log.error(feil) { "Feil under oppstart av bakgrunnsprosesser - stopper ${startede.size} allerede startet prosess(er)" }
        stoppBakgrunnsprosesser(log = log, bakgrunnsprosesser = startede)
        throw feil
    }
    return startede
}

internal fun stoppBakgrunnsprosesser(
    log: KLogger,
    bakgrunnsprosesser: List<StoppbarBakgrunnsprosess>,
) {
    if (bakgrunnsprosesser.isEmpty()) {
        log.info { "Ingen bakgrunnsprosesser å stoppe" }
        return
    }

    log.info { "Stopper ${bakgrunnsprosesser.size} bakgrunnsprosess(er)" }
    bakgrunnsprosesser.forEach { bakgrunnsprosess ->
        log.info { "Stopper ${bakgrunnsprosess.navn}" }
        Either.catch { bakgrunnsprosess.stopp() }
            .onLeft { log.error(it) { "Kunne ikke stoppe ${bakgrunnsprosess.navn} ved shutdown" } }
            .onRight { log.info { "Stoppet ${bakgrunnsprosess.navn}" } }
    }
    log.info { "Ferdig med å stoppe bakgrunnsprosesser" }
}

/**
 * Holder på livssyklusen til bakgrunnsprosessene og sørger for at start og stopp er trygge på tvers av tråder.
 *
 * Både [startVedServerReady] (kjøres på ServerReady-tråden) og [stopp] (kjøres på shutdown-tråden) tar samme lås.
 * Det gir disse garantiene:
 *  1. Bakgrunnsprosessene startes nøyaktig én gang, selv om [ServerReady] skulle fyres flere ganger.
 *  2. De stoppes nøyaktig én gang (felles `stoppet`-flagg), selv om både "shutdown kom mens vi startet"-grenen og [ApplicationStopping] prøver å stoppe.
 *  3. Hvis shutdown kommer mens vi starter, venter stoppen på at starten blir ferdig (samme lås), slik at vi aldri "mister" en nettopp startet prosess og lar den leve videre.
 *  4. Shutdown vinner alltid over readiness: `shutdownPågår` settes av shutdown-callbacken uten låsen, så vi re-sjekker flagget etter at readiness settes og ruller tilbake til ikke-klar + stopp dersom shutdown startet i mellomtiden.
 *  5. Stopp skjer i to faser: ved [ApplicationStopPreparing] signaliseres stopp ([påbegyntStoppVedShutdown]) slik at f.eks. Kafka slutter å plukke nye records med en gang, mens den blokkerende ventingen joines inn ved [ApplicationStopping] ([stopp]).
 */
private class Bakgrunnsprosesslivssyklus(
    private val log: KLogger,
    private val readiness: Readiness,
    private val shutdownPågår: AtomicBoolean,
    private val startBakgrunnsprosesser: () -> List<StoppbarBakgrunnsprosess>,
) {
    private val lås = Any()
    private var startet = false
    private var påbegyntStopp = false
    private var stoppet = false
    private var bakgrunnsprosesser: List<StoppbarBakgrunnsprosess> = emptyList()

    fun startVedServerReady() {
        synchronized(lås) {
            if (startet) {
                log.info { "ServerReady mottatt flere ganger - bakgrunnsprosesser er allerede startet" }
                return
            }

            if (stoppet || shutdownPågår.get()) {
                log.info { "ServerReady mottatt etter at shutdown har startet - starter ikke bakgrunnsprosesser" }
                return
            }

            log.info { "ServerReady mottatt - starter bakgrunnsprosesser" }
            bakgrunnsprosesser = startBakgrunnsprosesser()
            // Marker først som startet etter vellykket oppstart.
            // Kaster startBakgrunnsprosesser(), forblir startet = false slik at en ev. ny ServerReady kan forsøke på nytt (i stedet for å bli permanent NOT READY uten å prøve igjen).
            startet = true

            readiness.settKlar()
            log.info { "Bakgrunnsprosesser er startet - applikasjonen er klar" }

            // shutdownPågår kan settes på en annen tråd mellom toppsjekken og at vi setter readiness klar.
            // Shutdown-callbacken tar ikke samme lås, så uten denne re-sjekken kunne appen endt som READY etter at shutdown startet.
            // Shutdown skal alltid vinne, så vi ruller tilbake readiness og stopper prosessene dersom shutdown startet i mellomtiden.
            if (shutdownPågår.get()) {
                log.info { "Shutdown startet under oppstart - ruller tilbake readiness og stopper bakgrunnsprosesser" }
                readiness.settIkkeKlar()
                stoppInternt()
            }
        }
    }

    fun påbegyntStoppVedShutdown() {
        synchronized(lås) {
            if (påbegyntStopp) {
                return
            }
            påbegyntStopp = true

            if (bakgrunnsprosesser.isEmpty()) {
                return
            }
            log.info { "ApplicationStopPreparing mottatt - signaliserer stopp til ${bakgrunnsprosesser.size} bakgrunnsprosess(er)" }
            bakgrunnsprosesser.forEach { bakgrunnsprosess ->
                Either.catch { bakgrunnsprosess.påbegynStopp() }
                    .onLeft { log.error(it) { "Kunne ikke påbegynne stopp av ${bakgrunnsprosess.navn}" } }
            }
        }
    }

    fun stopp() {
        synchronized(lås) {
            log.info { "ApplicationStopping mottatt - stopper bakgrunnsprosesser" }
            stoppInternt()
        }
    }

    private fun stoppInternt() {
        if (stoppet) {
            log.info { "Bakgrunnsprosesser er allerede stoppet - ignorerer" }
            return
        }
        stoppet = true
        stoppBakgrunnsprosesser(log = log, bakgrunnsprosesser = bakgrunnsprosesser)
    }
}
