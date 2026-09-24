package fixtures.localdatetimenow

import java.time.Clock
import java.time.LocalDateTime

class Brudd(private val clock: Clock) {
    fun nåtid(): LocalDateTime = LocalDateTime.now(clock)

    fun nåtidUtenKlokke(): LocalDateTime = LocalDateTime.now()
}
