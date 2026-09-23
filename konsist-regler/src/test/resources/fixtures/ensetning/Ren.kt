package fixtures.ensetning

/**
 * Én setning per linje er greit.
 * Forkortelser som f.eks. denne flagges ikke.
 * Inline `kode. Med` punktum maskeres.
 *
 * Referanselenker uten avslutning på linjen over
 * https://example.com/docs/side
 * https://example.com/docs/annen-side
 */
class Ren {
    // En markørlinje under starter med stor bokstav:
    // Neste linje er et nytt avsnitt fordi forrige sluttet med kolon.
    fun noop() = Unit

    // Gyldig JSON, men ikke en kjent hendelsestype
    //language=json
    fun noop2() = Unit

    // Meldeperiode mandag til søndag
    // language=xml
    fun noop3() = Unit
}
