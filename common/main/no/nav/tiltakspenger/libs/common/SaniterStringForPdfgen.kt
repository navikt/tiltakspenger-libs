package no.nav.tiltakspenger.libs.common

/**
 * Pdfgen har problemer med å håndtere enkelte tegn.
 * Fjerner kontrolltegn i ascii-tabellen 0-31. Og tilsvarende unicode-tegn.
 */
object SaniterStringForPdfgen {

    val regexFilterLiteralControlCharacters = Regex("(\\\\u00[0-1][0-9A-F])")
    val regexFilterControlCharacters = Regex("[\\x00-\\x1F]")
    val regexFilterControlCharactersUnntattLineFeed = Regex("[\\x00-\\x09\\x0B-\\x1F]")

    fun saniter(input: String): String {
        return input
            .replace(regexFilterLiteralControlCharacters, "")
            .replace(regexFilterControlCharacters, "")
    }

    fun saniterBeholdNewline(input: String): String {
        return input
            .replace(regexFilterLiteralControlCharacters, "")
            .replace(regexFilterControlCharactersUnntattLineFeed, "")
    }
}
