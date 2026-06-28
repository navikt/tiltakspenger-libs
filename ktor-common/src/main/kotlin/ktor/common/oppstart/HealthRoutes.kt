package no.nav.tiltakspenger.libs.ktor.common.oppstart

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Helt åpne helse-endepunkter for NAIS:
 *  - `/isalive` svarer ALIVE (200 OK) så lenge prosessen kjører.
 *  - `/isready` svarer READY (200 OK) når [erKlar] er sann, ellers `503` med NOT READY.
 *
 * Registreres fra konsumentens `routing { healthRoutes(readiness::erKlar) }`.
 *
 * [erKlar] er et predikat slik at endepunktet ikke trenger å kjenne til hvordan readiness er implementert.
 * Til vanlig sendes [Readiness.erKlar] inn, men konsumenten kan sende inn hva som helst.
 * Tekstene kan overstyres per felt dersom et repo trenger noe annet i responsbody (defaultene er NAIS-konvensjon).
 *
 * @param erKlar Predikat for readiness som `/isready` leser.
 *
 * Se også:
 *   - https://doc.nais.io/workloads/explanations/good-practices/#implements-and-endpoints
 *   - https://doc.nais.io/workloads/application/reference/application-spec/#liveness
 *   - https://kubernetes.io/docs/concepts/configuration/liveness-readiness-startup-probes/
 *   - https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
 */
fun Route.healthRoutes(
    erKlar: () -> Boolean,
) {
    get("/isalive") {
        call.respondText("ALIVE")
    }
    get("/isready") {
        if (erKlar()) {
            call.respondText("READY")
        } else {
            call.respondText("NOT READY", status = HttpStatusCode.ServiceUnavailable)
        }
    }
}
