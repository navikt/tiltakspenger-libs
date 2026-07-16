package fixtures.ensetning

/**
 * Én setning per linje er greit.
 * Forkortelser som f.eks. denne flagges ikke.
 * Inline `kode. Med` punktum maskeres.
 */
class Ren {
    // En markørlinje under starter med stor bokstav:
    // Neste linje er et nytt avsnitt fordi forrige sluttet med kolon.
    fun noop() = Unit
}
