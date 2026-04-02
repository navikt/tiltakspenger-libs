package no.nav.tiltakspenger.libs.ktor.common

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import no.nav.tiltakspenger.libs.json.serialize

/**
 * @param json ferdigserialisert JSON-string. Obs: Det gjøres ingen validering på om dette er gyldig JSON.
 *
 * Defaulter til 200 OK og Content-Type: application/json; charset=UTF-8
 */
suspend inline fun ApplicationCall.respondJsonString(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    this.respondText(
        text = json,
        contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8),
        status = status,
    )
}

/**
 * Defaulter til 200 OK og Content-Type: application/json; charset=UTF-8
 * @throws IllegalArgumentException hvis T er String. Bruk respondJsonString(json = ...) for ferdigserialiserte strenger
 */
suspend inline fun <reified T : Any> ApplicationCall.respondJson(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    require(value !is String) {
        "Bruk respondJsonString(json = ...) for ferdigserialiserte strenger"
    }
    respondJsonString(json = serialize(value), status = status)
}

/** Denne er lagt til for å få compile feil istedenfor runtime feil */
@Suppress("unused", "RedundantSuspendModifier", "UnusedReceiverParameter")
@Deprecated(
    message = "Bruk respondJson(json = ...) for ferdigserialiserte strenger",
    level = DeprecationLevel.ERROR,
)
suspend inline fun ApplicationCall.respondJson(
    value: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): Nothing = error("Bruk respondJson(json = ...) for ferdigserialiserte strenger")

suspend inline fun ApplicationCall.respondStatus(status: HttpStatusCode) {
    this.respondText("", status = status)
}

suspend inline fun ApplicationCall.respondOk() {
    this.respondText("", status = HttpStatusCode.OK)
}

suspend inline fun ApplicationCall.respondNoContent() {
    this.respondText("", status = HttpStatusCode.NoContent)
}

/**
 * Defaulter til 200 OK og Content-Type: application/json; charset=UTF-8
 */
suspend inline fun <reified T : Any> ApplicationCall.respondJson(
    statusAndValue: Pair<HttpStatusCode, T>,
) {
    respondJson(
        value = statusAndValue.second,
        status = statusAndValue.first,
    )
}
