package fixtures.clockdefault

import java.time.Clock
import java.time.LocalDateTime

val clock: Clock = Clock.systemUTC()

class Ren(
    private val clock: Clock,
) {
    fun beregn(tidspunkt: LocalDateTime, klokke: Clock): LocalDateTime {
        val lokalKlokke: Clock = Clock.offset(klokke, java.time.Duration.ZERO)
        return LocalDateTime.now(lokalKlokke)
    }
}
