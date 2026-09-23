package no.nav.tiltakspenger.libs.ktor.common.oppstart

import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

/**
 * En bakgrunnsprosess (skedulert jobb, Kafka-consumer e.l.) som kan stoppes rent ved shutdown.
 *
 * Public fordi den injiseres i [konfigurerLivssyklus] og konstrueres i konsumentenes oppstartskode og tester.
 */
data class StoppbarBakgrunnsprosess(
    val navn: String,
    /**
     * Signaliserer stopp tidlig uten å blokkere lenge.
     * Kalles ved ApplicationStopPreparing.
     * Default: ingenting.
     */
    val påbegynStopp: () -> Unit = {},
    /**
     * Fullfører/venter på at stopp er ferdig.
     * Kalles ved ApplicationStopping.
     * Ligger sist så den kan brukes som trailing-lambda.
     */
    val stopp: () -> Unit,
)

/**
 * Pakker en blokkerende consumer-stopp ([stopp], typisk ManagedKafkaConsumer.stop) inn i en to-fase [StoppbarBakgrunnsprosess].
 * consumer.stop() slutter å polle umiddelbart, men venter så på at pågående batch behandles og committes (opptil shutdownTimeout).
 * Vi kjører [stopp] i en egen coroutine på [Dispatchers.IO] slik at consumeren slutter å plukke nye records i det shutdown starter (`påbegyntStopp` ved ApplicationStopPreparing).
 * Den blokkerende ventingen joines inn ved den endelige stoppen (`stop` ved ApplicationStopping), så nedstengningen overlapper HTTP-grace-perioden i stedet for å komme i tillegg.
 * Det dedikerte [stoppScope] rives ned igjen i `stop` (injiserbart for test).
 * Public pga. test.
 *
 * @param stoppScope Dedikert scope stopp-coroutinen kjøres i; injiserbart for test.
 * Default er et nytt scope på [Dispatchers.IO] med [SupervisorJob], og det rives ned (`cancel`) i `stop`.
 * @param stopp Den blokkerende stoppen som skal kjøres.
 * Må være selvstendig blokkerende og kan ikke avhenge av Ktor-/applikasjonstråder for å fullføre, siden den joines via `runBlocking` fra Ktor sin blokkerende shutdown-callback (ellers risikerer vi dødlås under shutdown).
 */
fun stoppbarKafkaConsumer(
    log: KLogger,
    navn: String,
    // SupervisorJob slik at en feilende stopp ikke kansellerer scopet, men i stedet fanges av Deferred og kastes ved await().
    stoppScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    stopp: () -> Unit,
): StoppbarBakgrunnsprosess {
    val stoppJobb = AtomicReference<Deferred<Unit>?>(null)

    fun påbegynnStopp() {
        // Vanlig tilfelle: stoppen er allerede igangsatt (påbegyntStopp + stop), så vi slipper å opprette en ny jobb.
        if (stoppJobb.get() != null) return
        // start = LAZY slik at kun den jobben som faktisk vinner compareAndSet blir startet (idempotent stopp).
        val nyJobb = stoppScope.async(start = CoroutineStart.LAZY) { stopp() }
        if (stoppJobb.compareAndSet(null, nyJobb)) {
            log.info { "Signaliserer stopp til $navn" }
            nyJobb.start()
        } else {
            // Tapte kappløpet (en annen tråd vant CAS).
            // Kanseller den ubrukte LAZY-jobben med en gang så den ikke henger igjen som et ikke-startet child i scopet til scopet rives ned.
            nyJobb.cancel()
        }
    }

    return StoppbarBakgrunnsprosess(
        navn = navn,
        stopp = {
            // Sikrer at stoppen er igangsatt (selv om ApplicationStopPreparing ikke ble fyrt) og venter på at den fullføres.
            påbegynnStopp()
            val jobb = stoppJobb.get()
            try {
                // stop() kalles fra Ktor sin blokkerende shutdown-callback, så vi joiner via runBlocking.
                // await() kaster videre en evt. feil fra stopp-coroutinen, slik at shutdown-problemer blir synlige (og håndteres/logges av stoppBakgrunnsprosesser).
                runBlocking { jobb?.await() }
            } catch (e: InterruptedException) {
                // runBlocking kaster InterruptedException hvis shutdown-tråden blir avbrutt mens vi venter.
                // Bevar interrupt-flagget slik at kallende kode ser avbruddet.
                // Rethrow slik at stoppBakgrunnsprosesser logger en ekte feil (onLeft) i stedet for falsk suksess ("Stoppet") - stoppen fullførte ikke, og kontrakten på stop ("Fullfører/venter") er brutt.
                Thread.currentThread().interrupt()
                throw e
            } finally {
                // Riv ned det dedikerte scopet når vi er ferdige med å vente, slik at vi ikke etterlater et levende scope etter shutdown.
                // Ble vi avbrutt, kanselleres en evt. fortsatt kjørende stopp-coroutine her (den blokkerende stoppen rekker uansett å fullføre siden den ikke er samarbeidende om kansellering).
                stoppScope.cancel()
            }
        },
        påbegynStopp = { påbegynnStopp() },
    )
}
