package fixtures.clocksystem

import java.time.Clock
import java.time.ZoneId

class Brudd {
    fun utc(): Clock = Clock.systemUTC()

    fun defaultSone(): Clock = Clock.systemDefaultZone()

    fun medSone(): Clock = Clock.system(ZoneId.of("Europe/Oslo")) // trailing kommentar unnskylder ikke kallet
}
