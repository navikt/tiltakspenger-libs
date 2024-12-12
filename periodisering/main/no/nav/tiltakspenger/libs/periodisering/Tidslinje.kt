package no.nav.tiltakspenger.libs.periodisering

/**
 * Periodiserer en liste av periodiserbare objekter, der vi beholder nyeste objekt for overlappende perioder.
 * Brukes for å lage en tidslinje av periodiserbare objekter, som f.eks. vedtak.
 */
fun <T : Periodiserbar> List<T>.toTidslinje(): Periodisering<T> {
    if (this.isEmpty()) return Periodisering(emptyList())
    if (this.size == 1) return Periodisering(this.first(), this.first().periode)
    this.map { it.opprettet }.distinct().let {
        require(it.size == this.size) { "Støtter ikke lage tidslinje når 2 elementer er opprettet samtidig." }
    }
    val sortedByDescending = this.sortedDescending()
    return sortedByDescending
        .drop(1)
        .fold(
            Periodisering(sortedByDescending.first(), sortedByDescending.first().periode),
        ) { akkumulerteVedtak, vedtak ->
            akkumulerteVedtak.utvid(vedtak, vedtak.periode)
        }
}
