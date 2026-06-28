package no.nav.tiltakspenger.libs.ktor.common.oppstart

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holder readiness-tilstanden (`/isready`) for appen.
 *
 * Eksplisitt instans som injiseres der den trengs, i stedet for global delt tilstand.
 * Den deles av tre uavhengige steder:
 *  - livssyklusen som setter appen klar/ikke-klar ([konfigurerLivssyklus]),
 *  - [healthRoutes] som svarer på `/isready` ([erKlar]),
 *  - konsumentens `RunCheckFactory` (libs:jobber) som lar skedulerte jobber kun kjøre når appen er klar (`isReady = readiness::erKlar`).
 *
 * Tilstanden er trådsikker ([AtomicBoolean]) fordi den leses/skrives fra både request-tråder, ServerReady-tråden og shutdown-tråden.
 * Hver app/test lager sin egen instans, så tester deler ikke tilstand med hverandre.
 */
class Readiness {
    private val klar = AtomicBoolean(false)

    /** Sann når appen er klar til å ta imot trafikk/kjøre jobber. */
    fun erKlar(): Boolean = klar.get()

    internal fun settKlar() {
        klar.set(true)
    }

    internal fun settIkkeKlar() {
        klar.set(false)
    }
}
