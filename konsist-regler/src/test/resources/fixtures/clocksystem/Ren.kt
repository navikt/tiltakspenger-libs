package fixtures.clocksystem

import java.time.Clock
import java.time.Instant

/**
 * Kall aldri Clock.systemUTC() utenfor composition root — KDoc-omtale skal ikke flagges.
 */
class Ren(private val clock: Clock) {
    // Utkommentert kode flagges heller ikke: val gammel = Clock.systemDefaultZone()
    fun tidsstempel(): Instant = Instant.now(clock)

    fun feilmelding(): String = "Bruk aldri Clock.systemUTC() i test" // Clock.system( i trailing kommentar er også greit
}
