package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.getOrElse
import java.nio.charset.Charset

/**
 * Lesbar tekst-representasjon av rå respons-bytes, brukt i [HttpKlientMetadata.rawResponseString] og [HttpKlientError.ResponsMottatt.body].
 * Disse strengene havner typisk i konsumentenes sikkerlogg, så de skal alltid være lesbar tekst: tekstlig innhold dekodes (charset fra `Content-Type`, default UTF-8), alt annet blir placeholderen fra [binærResponsPlaceholder].
 */
internal fun ByteArray.tilLesbarResponsString(responseHeaders: Map<String, List<String>>): String {
    val contentType = responseHeaders.contentTypeHeader()
    return if (erTekstligContentType(contentType)) String(this, charsetFra(contentType)) else binærResponsPlaceholder(size)
}

/**
 * Dekoder respons-bytene til tekst med charset fra `Content-Type` (default UTF-8) — samme oppførsel som JDK-ens `BodyHandlers.ofString()`.
 * Brukes for `String`-responser, der konsumenten eksplisitt har bedt om tekst og dekodingen derfor skjer uavhengig av om `Content-Type` regnes som tekstlig.
 */
internal fun ByteArray.dekodSomTekst(responseHeaders: Map<String, List<String>>): String =
    String(this, charsetFra(responseHeaders.contentTypeHeader()))

/**
 * Placeholderen som brukes i stedet for rå binærdata.
 * `HttpKlientFake` i `test-common` speiler dette formatet (holdes med vilje i synk; funksjonen er `private` og kan uansett ikke deles på tvers av modulene).
 */
private fun binærResponsPlaceholder(antallBytes: Int): String = "<binær respons, $antallBytes bytes>"

/** Første verdi av `Content-Type`-headeren, case-insensitivt (HTTP-headere er case-insensitive), eller `null` hvis den mangler. */
private fun Map<String, List<String>>.contentTypeHeader(): String? =
    entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.firstOrNull()

/**
 * `true` for innhold vi pragmatisk regner som tekstlig: media-typer med `text/`-prefiks, JSON og XML (inkludert `+json`/`+xml`-suffiksene fra RFC 6839).
 * En manglende `Content-Type` regnes også som tekstlig — det bevarer den gamle `ofString()`-oppførselen for servere som svarer med tekst uten å deklarere `Content-Type` (binære API-er deklarerer i praksis alltid sin type).
 */
private fun erTekstligContentType(contentType: String?): Boolean {
    if (contentType == null) return true
    val mediaType = contentType.substringBefore(';').trim().lowercase()
    val subtype = mediaType.substringAfter('/', missingDelimiterValue = "")
    return mediaType.startsWith("text/") ||
        subtype == "json" ||
        subtype.endsWith("+json") ||
        subtype == "xml" ||
        subtype.endsWith("+xml")
}

/**
 * Charset fra `charset`-parameteren i `Content-Type`, med UTF-8 som default — samme default som JDK-ens `BodyHandlers.ofString()`.
 * Parsingen er bevisst lempelig og godtar mellomrom rundt `=` (f.eks. `charset = ISO-8859-1`), selv om RFC 9110 §8.3.1 ikke tillater det — stille feildekoding er verre enn å avvise et sløvt formatert header.
 * Et ukjent/ugyldig charset-navn skal ikke velte kallet, så da faller vi også tilbake til UTF-8.
 */
private fun charsetFra(contentType: String?): Charset {
    val navn = contentType
        ?.split(';')
        ?.asSequence()
        ?.map { parameter -> parameter.split('=', limit = 2) }
        ?.firstOrNull { it.size == 2 && it[0].trim().equals("charset", ignoreCase = true) }
        ?.get(1)
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotEmpty() }
        ?: return Charsets.UTF_8
    return Either.catch { Charset.forName(navn) }.getOrElse { Charsets.UTF_8 }
}
