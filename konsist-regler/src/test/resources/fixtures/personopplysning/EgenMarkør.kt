package fixtures.personopplysning

// En markør et repo definerer selv, uten å arve fra rot-interfacet i libs.
// Standardsettet ser den ikke; repoet må legge navnet til.
interface Kontaktopplysning

data class LekkerTelefonnummer(val verdi: String) : Kontaktopplysning
