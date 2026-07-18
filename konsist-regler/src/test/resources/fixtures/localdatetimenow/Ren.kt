package fixtures.localdatetimenow

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Bruk nå(clock), ikke LocalDateTime.now(clock) — KDoc-omtale skal ikke flagges.
 */
class Ren(private val clock: Clock) {
    // Utkommentert kode flagges heller ikke: val gammel = LocalDateTime.now(clock)
    fun idag(): LocalDate = LocalDate.now(clock) // LocalDateTime.now i trailing kommentar er greit

    fun melding(): String = "tekst om LocalDateTime.now(clock) i en strengliteral er heller ikke et kall"
}
