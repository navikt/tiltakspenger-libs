package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Kildesystemets id for deltakelsen.
 *
 * Formatet varierer med [Tiltakskilde]: Arena gir `TA1234567`, Komet og Team Tiltak gir en UUID.
 * Vi validerer bevisst ikke formatet — formatet er kildens ansvar, og en id vi ikke kjenner formen på er fortsatt en id vi må kunne bære.
 * Bruk [harTaPrefiks] og [erUuid] til å kjenne igjen formen der det trengs, i stedet for å parse verdien på nytt på hvert kallsted.
 *
 * Verdien er den samme som lagres i `ekstern_id` i saksbehandling-api og som søknadsfrontenden sender som `aktivitetId`.
 * Den kan endres av kildesystemet over tid, så den skal ikke brukes som vår egen nøkkel.
 */
@JvmInline
value class EksternDeltakelseId(
    val verdi: String,
) {
    init {
        require(verdi.isNotBlank()) { "EksternDeltakelseId kan ikke være tom" }
    }

    /**
     * Arena-deltakelser får id på formen `TA1234567`, satt sammen av prefikset og Arenas egen numeriske id.
     * Merk at dette er en formsjekk, ikke en kildesjekk: [Kildestatus.kilde] er fasit på hvor deltakelsen kommer fra.
     */
    val harTaPrefiks: Boolean get() = verdi.startsWith("TA")

    /**
     * Komet og Team Tiltak identifiserer deltakelser med UUID.
     *
     * Sjekken er en regex og ikke `UUID.fromString`, av to grunner: den kaster ikke, og den er strengere.
     * `UUID.fromString` godtar en del som ikke er kanonisk UUID (for eksempel for korte grupper), og ville sagt ja til verdier vi ikke ville kalt en UUID.
     */
    val erUuid: Boolean get() = UUID_FORMAT.matches(verdi)

    override fun toString(): String = verdi
}

private val UUID_FORMAT = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")
