package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenClockSystemTest {
    private val scope = fixtureScope("clocksystem")

    @Test
    fun `flagger systemUTC, systemDefaultZone og system med sone`() {
        val brudd = IngenClockSystem.brudd(scope)

        brudd shouldHaveSize 3
        brudd[0] shouldContain "Clock.systemUTC("
        brudd[1] shouldContain "Clock.systemDefaultZone("
        brudd[2] shouldContain "Clock.system("
    }

    @Test
    fun `Clock som parameter og Clock-system i kommentarer og strenger flagges ikke`() {
        val brudd = IngenClockSystem.brudd(scope).filter { it.contains("Ren.kt") }

        brudd.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        val brudd = IngenClockSystem.brudd(scope, unntatteFilstier = setOf("clocksystem/Brudd.kt"))

        brudd.shouldBeEmpty()
    }
}
