package no.nav.tiltakspenger.libs.periodisering

/**
 * Periodiserer en liste av periodiserbare objekter, der vi beholder nyeste objekt for overlappende perioder.
 * Brukes for å lage en tidslinje av periodiserbare objekter, som f.eks. vedtak.
 */
fun <T : Periodiserbar> List<T>.toTidslinje(): Periodisering<T> {
    if (this.isEmpty()) return Periodisering.empty()
    if (this.size == 1) return SammenhengendePeriodisering(this.first(), this.first().periode)

    this.map { it.opprettet }.distinct().let {
        require(it.size == this.size) { "Støtter ikke lage tidslinje når 2 elementer er opprettet samtidig." }
    }

    val sortedByDescending = this.sorted()
    return sortedByDescending
        .fold(
            Periodisering.empty<T>() as Periodisering<T>,
        ) { akkumulerteVedtak, vedtak ->
            akkumulerteVedtak.setVerdiForDelperiode(vedtak, vedtak.periode)
        }
}
