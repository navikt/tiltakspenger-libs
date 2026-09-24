package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class IngenNowUtenClockTest {
    private val scope = fixtureScope("nowutenclock")

    @Test
    fun `flagger no-arg now-kall i kildekode`() {
        val brudd = IngenNowUtenClock.brudd(scope)

        brudd shouldHaveSize 3
        brudd[0] shouldContain "LocalDateTime.now()"
        brudd[1] shouldContain "Instant.now()"
        brudd[2] shouldContain "LocalDate.now()"
    }

    @Test
    fun `now med Clock-parameter og now i kommentarer flagges ikke`() {
        val brudd = IngenNowUtenClock.brudd(scope).filter { it.contains("Ren.kt") }

        brudd.shouldBeEmpty()
    }

    /** En egen tidstype med samme no-arg-mønster fanges først når repoet legger typenavnet til. */
    @Test
    fun `ekstra typer utvider standardsettet`() {
        IngenNowUtenClock.brudd(scope).joinToString("\n") shouldNotContain "Virkedag"

        val brudd = IngenNowUtenClock.brudd(scope, ekstraTyper = setOf("Virkedag"))

        brudd shouldHaveSize 4
        brudd.joinToString("\n") shouldContain "Virkedag.now()"
    }
}
