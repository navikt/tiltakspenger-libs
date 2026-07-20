package no.nav.tiltakspenger.libs.ktor.test.common

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode

/**
 * Forventninger til responsen i [defaultRequestWithAssertions].
 * [status] assertes alltid.
 * [body] assertes etter valgt [ForventetBody]-variant; `null` betyr at bodyen ikke assertes.
 * [contentType] asserter responsens Content-Type når den er satt; `null` betyr at Content-Type ikke assertes.
 * [ForventetBody.Tom] kan ikke kombineres med [contentType], siden Tom allerede krever at responsen ikke har Content-Type.
 */
data class ForventetRespons(
    val status: HttpStatusCode,
    val body: ForventetBody? = null,
    val contentType: ContentType? = null,
) {
    init {
        require(body !is ForventetBody.Tom || contentType == null) {
            "ForventetBody.Tom krever at responsen ikke har Content-Type, så contentType kan ikke settes samtidig"
        }
    }
}

/**
 * Måten responsbodyen assertes på i [ForventetRespons].
 */
sealed interface ForventetBody {
    /** Asserter at bodyen er tom og at responsen ikke har Content-Type. */
    data object Tom : ForventetBody

    /** Asserter eksakt strenglikhet mot bodyen. */
    data class Eksakt(val verdi: String) : ForventetBody

    /** Asserter JSON-likhet mot bodyen. */
    data class Json(val verdi: String) : ForventetBody
}
