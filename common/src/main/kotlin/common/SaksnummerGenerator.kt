package no.nav.tiltakspenger.libs.common

import java.time.LocalDate

interface SaksnummerGenerator {
    fun generer(dato: LocalDate): Saksnummer

    data object Prod : SaksnummerGenerator {
        override fun generer(dato: LocalDate): Saksnummer {
            return Saksnummer.genererSaknummer(dato = dato, løpenr = "0001")
        }
    }
}
