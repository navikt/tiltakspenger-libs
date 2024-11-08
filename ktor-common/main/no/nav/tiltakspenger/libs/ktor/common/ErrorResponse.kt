package no.nav.tiltakspenger.libs.ktor.common

import io.ktor.http.HttpStatusCode

data class ErrorResponse(
    val json: ErrorJson,
    val httpStatus: HttpStatusCode,
)
