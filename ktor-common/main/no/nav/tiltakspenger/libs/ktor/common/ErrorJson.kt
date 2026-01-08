@file:Suppress("unused")

package no.nav.tiltakspenger.libs.ktor.common

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import no.nav.tiltakspenger.libs.json.serialize

data class ErrorJson(
    val melding: String,
    val kode: String,
)

suspend inline fun ApplicationCall.respond403Forbidden(melding: String, kode: String) {
    this.respondError(
        status = HttpStatusCode.Forbidden,
        melding = melding,
        kode = kode,
    )
}

suspend inline fun ApplicationCall.respond403Forbidden(errorJson: ErrorJson) {
    this.respondError(HttpStatusCode.Forbidden, errorJson)
}

suspend inline fun ApplicationCall.respond401Unauthorized(melding: String, kode: String) {
    this.respondError(
        status = HttpStatusCode.Unauthorized,
        melding = melding,
        kode = kode,
    )
}

suspend inline fun ApplicationCall.respond401Unauthorized(errorJson: ErrorJson) {
    this.respondError(HttpStatusCode.Unauthorized, errorJson)
}

suspend inline fun ApplicationCall.respond500InternalServerError(melding: String, kode: String) {
    this.respondError(
        status = HttpStatusCode.InternalServerError,
        melding = melding,
        kode = kode,
    )
}

suspend inline fun ApplicationCall.respond500InternalServerError(errorJson: ErrorJson) {
    this.respondError(HttpStatusCode.InternalServerError, errorJson)
}

suspend inline fun ApplicationCall.respond400BadRequest(melding: String, kode: String) {
    this.respondError(
        status = HttpStatusCode.BadRequest,
        melding = melding,
        kode = kode,
    )
}

suspend inline fun ApplicationCall.respond400BadRequest(errorJson: ErrorJson) {
    this.respondError(HttpStatusCode.BadRequest, errorJson)
}

suspend inline fun ApplicationCall.respond404NotFound(melding: String, kode: String) {
    this.respondError(
        status = HttpStatusCode.NotFound,
        melding = melding,
        kode = kode,
    )
}

suspend inline fun ApplicationCall.respond404NotFound(errorJson: ErrorJson) {
    this.respondError(HttpStatusCode.NotFound, errorJson)
}

suspend inline fun ApplicationCall.respond409Conflict(melding: String, kode: String) {
    this.respondError(
        status = HttpStatusCode.Conflict,
        melding = melding,
        kode = kode,
    )
}

suspend inline fun ApplicationCall.respond409Conflict(errorJson: ErrorJson) {
    this.respondError(HttpStatusCode.Conflict, errorJson)
}

suspend inline fun ApplicationCall.respond501NotImplemented(melding: String, kode: String) {
    this.respondError(HttpStatusCode.NotImplemented, ErrorJson(melding, kode))
}

suspend inline fun ApplicationCall.respond501NotImplemented(errorJson: ErrorJson) {
    this.respondError(HttpStatusCode.NotImplemented, errorJson)
}

suspend inline fun ApplicationCall.respondError(status: HttpStatusCode, melding: String, kode: String) {
    this.respondError(
        status = status,
        errorJson = ErrorJson(
            melding = melding,
            kode = kode,
        ),
    )
}

suspend inline fun ApplicationCall.respondError(status: HttpStatusCode, errorJson: ErrorJson) {
    this.respondText(
        text = serialize(errorJson),
        contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8),
        status = status,
    )
}
