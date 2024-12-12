package no.nav.tiltakspenger.libs.periodisering

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Sorterbar på opprettet-tidspunkt.
 * @throws IllegalArgumentException hvis to periodiserbare har samme opprettet-tidspunkt.
 */
interface Periodiserbar : Comparable<Periodiserbar> {
    val periode: Periode
    val opprettet: LocalDateTime

    val fraOgMed: LocalDate get() = periode.fraOgMed
    val tilOgMed: LocalDate get() = periode.tilOgMed

    override fun compareTo(other: Periodiserbar): Int {
        if (opprettet == other.opprettet) {
            throw IllegalArgumentException("Det gir ikke mening og sammenligne to periodiseringsbare med samme opprettet-tidspunkt.")
        }
        return opprettet.compareTo(other.opprettet)
    }
}
