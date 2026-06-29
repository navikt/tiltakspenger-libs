package no.nav.tiltakspenger.libs.jobber

import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Felles coroutine-scope for skeduleringsjobbene i modulen.
 *
 * Én scope per executor; hver loop launches via [start].
 * [stop] er idempotent, kansellerer scope-et og venter til alle loops er ferdige.
 * Selve task-kjøringen forventes wrappet i [kotlinx.coroutines.NonCancellable] slik at en pågående syklus alltid fullfører før [stop] returnerer.
 */
internal class Skeduleringsscope(
    private val jobName: String,
    private val log: KLogger,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stoppet = AtomicBoolean(false)
    private val loops = mutableListOf<Job>()

    fun start(loop: suspend CoroutineScope.() -> Unit) {
        loops += scope.launch(block = loop)
    }

    /**
     * Idempotent.
     * Stopper å starte nye kjøringer; pågående kjøring får fullføre (task-kjøring er [kotlinx.coroutines.NonCancellable]).
     */
    fun stop() {
        if (!stoppet.compareAndSet(false, true)) return
        scope.cancel()
        runBlocking { loops.joinAll() }
        log.info { "Skeduleringsjobb '$jobName' stoppet. Pågående kjøringer ferdigstilles." }
    }
}
