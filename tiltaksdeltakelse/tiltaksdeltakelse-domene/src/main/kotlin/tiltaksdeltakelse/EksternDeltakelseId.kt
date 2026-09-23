package no.nav.tiltakspenger.libs.tiltaksdeltakelse

/**
 * Kildesystemets id for deltakelsen.
 *
 * Formatet varierer med [Tiltakskilde]: Arena gir `TA1234567`, Komet og Team Tiltak gir en UUID.
 * Vi validerer bevisst ikke formatet — formatet er kildens ansvar, og en id vi ikke kjenner formen på er fortsatt en id vi må kunne bære.
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

    override fun toString(): String = verdi
}
