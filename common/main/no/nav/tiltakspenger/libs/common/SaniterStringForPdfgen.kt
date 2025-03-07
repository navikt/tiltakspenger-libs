package no.nav.tiltakspenger.libs.common

/**
 * Pdfgen har problemer med å håndtere enkelte tegn.
 * Fjerner kontrolltegn i ascii-tabellen 0-31. Og tilsvarende unicode-tegn.
 */
object SaniterStringForPdfgen {

    val regexFilterLiteralControlCharacters = Regex("(\\\\u00[0-1][0-9A-F])")
    val regexFilterControlCharacters = Regex("[\\x00-\\x1F]")

    fun saniter(input: String): String {
        return input
            .replace(regexFilterLiteralControlCharacters, "")
            .replace(regexFilterControlCharacters, "")
    }
}
