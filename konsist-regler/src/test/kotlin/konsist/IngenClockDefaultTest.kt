package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenClockDefaultTest {
    private val scope = fixtureScope("clockdefault")

    @Test
    fun `flagger Clock-parametre med default-verdi i konstruktør og funksjon`() {
        val brudd = IngenClockDefault.brudd(scope)

        brudd shouldHaveSize 2
        brudd.first() shouldContain "clock"
        brudd.last() shouldContain "klokke"
    }

    @Test
    fun `påkrevde Clock-parametre og Clock-properties flagges ikke`() {
        val brudd = IngenClockDefault.brudd(scope).filter { it.contains("Ren.kt") }

        brudd.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        val brudd = IngenClockDefault.brudd(scope, unntatteFilstier = setOf("clockdefault/Brudd.kt"))

        brudd.shouldBeEmpty()
    }
}
