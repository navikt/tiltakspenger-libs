package no.nav.tiltakspenger.libs.httpklient.infra.kall

/**
 * Én del i en `multipart/form-data`-body — feltnavn, filnavn, delens egen `Content-Type` og rå bytes.
 * Filopplasting er foreløpig det eneste reelle behovet (ClamAV-virusskanning av vedlegg); rene tekstfelter legges til her når et endepunkt trenger dem.
 *
 * [feltnavn] og [filnavn] havner i delens `Content-Disposition`-header, så CR/LF avvises fail-fast her (header-injeksjon) på samme måte som [Header] avviser reserverte navn.
 * Anførselstegn og backslash er derimot lovlige i filnavn og escapes ved enkoding, slik at et brukeropplastet vedlegg aldri velter kallet.
 *
 * Ikke en data class: den genererte `equals` ville sammenlignet [innhold] med referanselikhet, og `toString` ville skrevet ut en array-referanse.
 * Begge er skrevet for hånd nedenfor i stedet — verdilikhet er det [MultipartDeler] trenger for å være en meningsfull data class.
 */
class MultipartDel(
    val feltnavn: String,
    val filnavn: String,
    val contentType: String,
    val innhold: ByteArray,
) {
    init {
        require(feltnavn.isNotBlank()) { "feltnavn kan ikke være blankt" }
        require(filnavn.isNotBlank()) { "filnavn kan ikke være blankt" }
        require(contentType.isNotBlank()) { "contentType kan ikke være blank" }
        require(feltnavn.erUtenLinjeskift()) { "feltnavn kan ikke inneholde linjeskift, var '$feltnavn'" }
        require(filnavn.erUtenLinjeskift()) { "filnavn kan ikke inneholde linjeskift, var '$filnavn'" }
        require(contentType.erUtenLinjeskift()) { "contentType kan ikke inneholde linjeskift, var '$contentType'" }
    }

    /**
     * Verdilikhet, med [innhold] sammenlignet på innhold (`contentEquals`) og ikke på referanse.
     * Metadata-feltene sjekkes først slik at to deler med ulikt filnavn avvises uten å røre bytene.
     */
    override fun equals(other: Any?): Boolean =
        other is MultipartDel &&
            feltnavn == other.feltnavn &&
            filnavn == other.filnavn &&
            contentType == other.contentType &&
            innhold.contentEquals(other.innhold)

    /**
     * Bevisst uten [innhold]: `contentHashCode` er O(n), og et vedlegg kan være flere megabyte.
     * Kontrakten holder likevel — like objekter gir lik hash — fordi hashen bygges av en delmengde av feltene [equals] bruker.
     * Kollisjoner er uansett uinteressante i praksis: [MultipartDeler] krever unike feltnavn.
     */
    override fun hashCode(): Int {
        var result = feltnavn.hashCode()
        result = 31 * result + filnavn.hashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }

    /** Filinnholdet gjengis som antall bytes, aldri som innhold — samme regel som resten av `httpklient` følger for binære bodyer. */
    override fun toString(): String =
        "MultipartDel(feltnavn=$feltnavn, filnavn=$filnavn, contentType=$contentType, innhold=<${innhold.size} bytes>)"
}

/** CR/LF i en headerverdi lar en angriper injisere egne headere eller deler; verdiene avvises i stedet for å bli strippet i det stille. */
internal fun String.erUtenLinjeskift(): Boolean = none { it == '\r' || it == '\n' }
