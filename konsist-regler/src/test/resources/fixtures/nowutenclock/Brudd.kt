package fixtures.nowutenclock

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class Brudd {
    fun nåtid(): LocalDateTime = LocalDateTime.now()

    fun tidsstempel(): Instant = Instant.now()

    fun idag(): LocalDate = LocalDate.now() // trailing kommentar unnskylder ikke kallet
}
