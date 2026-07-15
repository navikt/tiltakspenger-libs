package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.common.AccessToken

/**
 * Materialiserer `Authorization: Bearer <token>` på headerne.
 * Invariant: headeren kan aldri være satt fra før — [Header] avviser reserverte navn, så klienten er alene om å skrive den.
 */
internal fun Map<String, List<String>>.withBearerToken(accessToken: AccessToken): Map<String, List<String>> =
    buildMap(size + 1) {
        putAll(this@withBearerToken)
        put("Authorization", listOf("Bearer ${accessToken.token}"))
    }

/**
 * Headernavn (case-insensitivt) hvis verdier maskeres når headere gjengis i `rawRequestString`.
 * Dette gjelder kun tekstrepresentasjonen; den faktiske HTTP-requesten sendes med ekte verdier.
 */
private val sensitiveHeaderNames = setOf("authorization", "proxy-authorization", "cookie", "set-cookie")

/**
 * Returnerer headerne med verdiene til sensitive headere erstattet med `***`.
 * Standardsettet (auth/cookie) utvides med [ekstraSensitive] — lowercase-navnene på konsument-headere markert [Header.sensitiv] (f.eks. en `ident`-header med fnr).
 * Brukes før headere puttes inn i [HttpKlientMetadata.rawRequestString], slik at bearer-tokens og PII ikke lekker til logger.
 */
internal fun Map<String, List<String>>.redactSensitiveHeaders(
    ekstraSensitive: Set<String>,
): Map<String, List<String>> {
    val skalMaskeres = { navn: String -> navn.lowercase() in sensitiveHeaderNames || navn.lowercase() in ekstraSensitive }
    if (keys.none(skalMaskeres)) return this
    return mapValues { (name, values) ->
        if (skalMaskeres(name)) values.map { "***" } else values
    }
}
