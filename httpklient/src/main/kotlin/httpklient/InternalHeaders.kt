package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.common.AccessToken

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
 * Setter [defaults] hvis de mangler (case-insensitivt).
 * Allokerer maks ett nytt Map per kall, og _bare_ hvis minst én default mangler; ellers returneres opprinnelig Map uendret.
 * Bevarer iterasjonsrekkefølgen til opprinnelig Map, med nye headere lagt til til slutt.
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

/**
 * Headernavn (case-insensitivt) hvis verdier maskeres når headere gjengis i `rawRequestString` eller logges.
 * Dette gjelder kun tekstrepresentasjonen/loggingen; den faktiske HTTP-requesten sendes med ekte verdier.
 */
private val sensitiveHeaderNames = setOf("authorization", "proxy-authorization", "cookie", "set-cookie")

/**
 * Returnerer headerne med verdiene til sensitive headere (f.eks. `Authorization`) erstattet med `***`.
 * Brukes før headere puttes inn i [HttpKlientMetadata.rawRequestString] eller logges, slik at bearer-tokens og lignende ikke lekker til logger.
 * Eksponert (ikke `internal`) slik at `HttpKlientFake` i `test-common` kan gjenbruke nøyaktig samme redaksjonslogikk og ikke divergere fra produksjonsklienten.
 */
fun Map<String, List<String>>.redactSensitiveHeaders(): Map<String, List<String>> {
    if (keys.none { it.lowercase() in sensitiveHeaderNames }) return this
    return mapValues { (name, values) ->
        if (name.lowercase() in sensitiveHeaderNames) values.map { "***" } else values
    }
}
