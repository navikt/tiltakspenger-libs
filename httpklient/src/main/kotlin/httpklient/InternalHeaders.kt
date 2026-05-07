package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.common.AccessToken

internal fun BuiltHttpKlientRequest.effectiveRequestHeaders(): Map<String, List<String>> {
    return when (body) {
        is RequestBody.Json, is RequestBody.RawJson -> headers.withDefaultJsonContentTypeHeader()
        else -> headers
    }
}

internal fun Map<String, List<String>>.withDefaultJsonContentTypeHeader(): Map<String, List<String>> {
    return withDefaultHeaders("Content-Type" to "application/json")
}

internal fun Map<String, List<String>>.withDefaultJsonAcceptHeader(): Map<String, List<String>> {
    return withDefaultHeaders("Accept" to "application/json")
}

/**
 * Setter `Authorization: Bearer <token>` hvis headeren ikke allerede er satt (case-insensitivt).
 * Hvis konsumenten har satt `Authorization` eksplisitt, beholdes deres verdi uendret.
 */
internal fun Map<String, List<String>>.withBearerToken(accessToken: AccessToken): Map<String, List<String>> {
    return withDefaultHeaders("Authorization" to "Bearer ${accessToken.token}")
}

/**
 * Setter [defaults] hvis de mangler (case-insensitivt). Allokerer maks ett nytt kart per kall, og
 * _bare_ hvis minst én default mangler; ellers returneres opprinnelig kart uendret. Bevarer
 * iterasjonsrekkefølgen til opprinnelig kart, med nye headere lagt til til slutt.
 */
private fun Map<String, List<String>>.withDefaultHeaders(
    vararg defaults: Pair<String, String>,
): Map<String, List<String>> {
    val missing = defaults.filterNot { (name, _) -> containsHeaderIgnoreCase(name) }
    if (missing.isEmpty()) return this
    return buildMap(size + missing.size) {
        putAll(this@withDefaultHeaders)
        missing.forEach { (name, value) -> put(name, listOf(value)) }
    }
}

private fun Map<String, List<String>>.containsHeaderIgnoreCase(name: String): Boolean =
    keys.any { it.equals(name, ignoreCase = true) }
