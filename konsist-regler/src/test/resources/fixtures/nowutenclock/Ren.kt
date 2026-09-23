package fixtures.nowutenclock

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Kall aldri LocalDateTime.now() uten klokke — KDoc-omtale skal ikke flagges.
 */
class Ren(private val clock: Clock) {
    // Utkommentert kode flagges heller ikke: val gammel = LocalDate.now()
    fun nåtid(): LocalDateTime = LocalDateTime.now(clock)

    fun tidsstempel(): Instant = Instant.now(clock)

    fun idag(): LocalDate = LocalDate.now(clock) // now() i trailing kommentar er også greit
}
