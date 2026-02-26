package no.nav.tiltakspenger.libs.ktor.common

import io.ktor.http.HttpStatusCode

@Deprecated("Bruk  no.nav.tiltakspenger.libs.ktor.common.ErrorJson")
data class ErrorResponse(
    val json: ErrorJsonBase,
    val httpStatus: HttpStatusCode,
)
