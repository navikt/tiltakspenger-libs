package fixtures.clockdefault

import java.time.Clock
import java.time.LocalDateTime

class Brudd(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun beregn(tidspunkt: LocalDateTime, klokke: Clock = Clock.systemDefaultZone()): LocalDateTime = tidspunkt
}
