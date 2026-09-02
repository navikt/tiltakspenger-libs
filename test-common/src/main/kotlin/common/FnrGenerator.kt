package no.nav.tiltakspenger.libs.common

import arrow.atomic.Atomic

/**
 * Trådsikker.
 * Teller oppover fra [start] og null-padder til 11 siffer, så hvert kall gir et nytt, unikt fnr.
 * Genererer ikke fnr over 99999900000, som er reservert for hardkodede fnr.
 */
class FnrGenerator(
    start: Long = 0L,
) {
    private val neste = Atomic(start)

    fun generer(): Fnr {
        val nr = neste.getAndUpdate {
            check(it <= MAKS_GENERERT_FNR) { "Kan ikke generere fnr over $MAKS_GENERERT_FNR." }
            it + 1
        }
        return Fnr.fromString(nr.toString().padStart(11, '0'))
    }

    private companion object {
        const val MAKS_GENERERT_FNR = 99_999_900_000L
    }
}
