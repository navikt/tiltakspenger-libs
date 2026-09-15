package no.nav.tiltakspenger.libs.common.personopplysning

/**
 * Markerer utvalgte verdityper som personopplysninger.
 * Implementasjonene maskerer `toString()`, og en `data class` som har typen som felt arver maskeringen i sin genererte `toString()`.
 * Råverdi, json-serialisering og lagring ligger utenfor beskyttelsen.
 * Det lukkede hierarkiet er et hjelpemiddel for PVK, ikke en komplett liste over personopplysninger tjenestene behandler.
 * Nye typer legges i denne pakken med egen fil, KDoc etter normen, `begrunnelse` og test.
 */
sealed interface Personopplysning {
    /** Hva verdien kan røpe om personen, statisk per type og lesbart for andre enn utviklere, som grunnlag for PVK. */
    val begrunnelse: String

    override fun toString(): String
}
