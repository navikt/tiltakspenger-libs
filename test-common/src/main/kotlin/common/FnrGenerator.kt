package no.nav.tiltakspenger.libs.common

import arrow.atomic.Atomic

/**
 * Trådsikker.
 * Bruker [start] som nullbasert indeks i sekvensen, så hvert kall gir et nytt, unikt fnr.
 * Genererer bare 11-sifrede verdier der det tredje sifferet er 8 eller 9, i tråd med 2032 standard fra folkeregisteret for syntetiske fnr.
 * Genererer ikke fnr over 99999900000, som er reservert for hardkodede fnr.
 */
class FnrGenerator(
    start: Long = 0L,
) {
    init {
        require(start >= 0) { "Start kan ikke være negativ." }
        require(start <= MAKS_INDEKS) { "Start kan ikke være større enn $MAKS_INDEKS." }
    }

    private val neste = Atomic(start)

    fun generer(): Fnr {
        val indeks = neste.getAndUpdate {
            check(it <= MAKS_INDEKS) { "Kan ikke generere fnr over $MAKS_GENERERT_FNR." }
            it + 1
        }
        val førsteToSifre = indeks / ANTALL_PER_PREFIKS
        val indeksIPrefiks = indeks % ANTALL_PER_PREFIKS
        val tredjeSiffer = 8 + indeksIPrefiks / ANTALL_PER_TREDJE_SIFFER
        val sisteÅtteSifre = indeksIPrefiks % ANTALL_PER_TREDJE_SIFFER
        val nr = førsteToSifre * PREFIKS_PLASSVERDI + tredjeSiffer * TREDJE_SIFFER_PLASSVERDI + sisteÅtteSifre
        return Fnr.fromString(nr.toString().padStart(11, '0'))
    }

    private companion object {
        const val ANTALL_PER_TREDJE_SIFFER = 100_000_000L
        const val ANTALL_PER_PREFIKS = 2 * ANTALL_PER_TREDJE_SIFFER
        const val PREFIKS_PLASSVERDI = 1_000_000_000L
        const val TREDJE_SIFFER_PLASSVERDI = 100_000_000L
        const val MAKS_GENERERT_FNR = 99_999_900_000L
        const val MAKS_INDEKS = 19_999_900_000L
    }
}
