package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenLocalDateTimeNowTest {
    private val scope = fixtureScope("localdatetimenow")

    @Test
    fun `flagger LocalDateTime-now med og uten argument`() {
        val brudd = IngenLocalDateTimeNow.brudd(scope)

        brudd shouldHaveSize 2
        brudd.first() shouldContain "LocalDateTime.now(clock)"
        brudd.last() shouldContain "LocalDateTime.now()"
    }

    @Test
    fun `kommentarer, trailing-kommentarer og andre typers now flagges ikke`() {
        val brudd = IngenLocalDateTimeNow.brudd(scope).filter { it.contains("Ren.kt") }

        brudd.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        val brudd = IngenLocalDateTimeNow.brudd(scope, unntatteFilstier = setOf("localdatetimenow/Brudd.kt"))

        brudd.shouldBeEmpty()
    }
}
