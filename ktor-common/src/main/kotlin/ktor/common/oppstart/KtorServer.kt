package no.nav.tiltakspenger.libs.ktor.common.oppstart

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starter en embedded Netty-server med felles oppsett for graceful shutdown og håndtering av SIGTERM-under-oppstart-racet.
 *
 * Konsumenten setter opp appen i [module]: typisk `ktorSetup(...)` (plugins, auth, routes inkl. [healthRoutes]) etterfulgt av [konfigurerLivssyklus].
 * [module] får [AtomicBoolean]-flagget `shutdownPågår` som må sendes videre til [konfigurerLivssyklus], slik at både server-bootstrap og livssyklus deler samme shutdown-tilstand.
 *
 * Eksempel (i konsumentens `start()`):
 * ```
 * val readiness = Readiness()
 * startKtorServer(log = log, port = httpPort()) { shutdownPågår ->
 *     ktorSetup(applicationContext) // setter opp routes inkl. healthRoutes(readiness)
 *     konfigurerLivssyklus(log = log, readiness = readiness, shutdownPågår = shutdownPågår, startBakgrunnsprosesser = { ... })
 * }
 * ```
 *
 * @param host Nettverksgrensesnittet serveren binder til.
 * Default `0.0.0.0` (alle grensesnitt) er nødvendig i Nais-pods og containere, der trafikken kommer inn utenfra.
 * Lokale kjøringer (typisk LokalMain) bør sende `127.0.0.1` slik at serveren ikke eksponeres på nettverket maskinen står på.
 * @param module Kjøres som Ktor-modul.
 * Får `shutdownPågår` som skal sendes inn i [konfigurerLivssyklus].
 */
fun startKtorServer(
    log: KLogger,
    port: Int,
    host: String = "0.0.0.0",
    shutdownGracePeriodMillis: Long = 5_000,
    shutdownTimeoutMillis: Long = 30_000,
    module: Application.(shutdownPågår: AtomicBoolean) -> Unit,
) {
    log.info { "starting server" }

    val shutdownPågår = AtomicBoolean(false)

    val server = embeddedServer(
        factory = Netty,
        configure = {
            connector {
                this.host = host
                this.port = port
            }
            shutdownGracePeriod = shutdownGracePeriodMillis
            shutdownTimeout = shutdownTimeoutMillis
        },
        module = {
            module(shutdownPågår)
        },
    )

    startOgHåndterOppstartsrace(log = log, shutdownPågår = shutdownPågår) {
        server.start(wait = true)
    }
}

/**
 * Kjører [startServer] (typisk `server.start(wait = true)`) og håndterer SIGTERM-under-oppstart-racet.
 * Trukket ut fra [startKtorServer] slik at selve race-håndteringen kan testes uten å starte en ekte Netty-server.
 *
 * Ved redeploy kan SIGTERM treffe akkurat mens Netty binder server-socketen.
 * Ktor registrerer sin egen shutdown hook i EmbeddedServer.start(), og dersom den rekker å terminere Netty EventLoopGroup før bind er ferdig, kan Netty kaste RejectedExecutionException("event executor terminated") fra start().
 * Det er en forventet shutdown-race, ikke en reell oppstartsfeil, så den svelges kun når [shutdownPågår] og feilen er Netty sin [erNettyEventExecutorTerminert]-feil.
 * Alle andre feil kastes videre.
 *
 * Kilder:
 * - Ktor lifecycle-events: https://ktor.io/docs/server-events.html
 * - Ktor shutdown: https://ktor.io/docs/server-shutdown.html
 * - Ktor EmbeddedServer.start/stop: https://github.com/ktorio/ktor/blob/3.4.3/ktor-server/ktor-server-core/jvm/src/io/ktor/server/engine/EmbeddedServerJvm.kt
 * - Ktor NettyApplicationEngine.start/stop: https://github.com/ktorio/ktor/blob/3.4.3/ktor-server/ktor-server-netty/jvm/src/io/ktor/server/netty/NettyApplicationEngine.kt
 * - Netty EventLoopGroup shutdownGracefully: https://netty.io/4.1/api/io/netty/channel/EventLoopGroup.html
 */
internal fun startOgHåndterOppstartsrace(
    log: KLogger,
    shutdownPågår: AtomicBoolean,
    startServer: () -> Unit,
) {
    try {
        startServer()
    } catch (e: RejectedExecutionException) {
        if (shutdownPågår.get() && e.erNettyEventExecutorTerminert()) {
            log.info(e) { "Ignorerer Netty startup-feil fordi shutdown allerede pågår" }
        } else {
            throw e
        }
    }
}

/**
 * Den eksakte (substring av) feilmeldingen Netty bruker når EventLoopGroup allerede er terminert.
 * Vi matcher på tekst fordi Netty ikke gir en mer spesifikk exception-type her, så denne må verifiseres ved oppgradering av Ktor/Netty (det finnes en test som låser eksakt streng: erNettyEventExecutorTerminert).
 */
private const val NETTY_EVENT_EXECUTOR_TERMINATED = "event executor terminated"

/**
 * Sjekker om feilen er Netty sin "event executor terminated"-feil som kan oppstå når SIGTERM treffer under oppstart.
 * Matcher på meldingstekst, så den må verifiseres ved oppgradering av Ktor/Netty.
 */
fun Throwable.erNettyEventExecutorTerminert(): Boolean =
    this is RejectedExecutionException && message?.contains(NETTY_EVENT_EXECUTOR_TERMINATED) == true
