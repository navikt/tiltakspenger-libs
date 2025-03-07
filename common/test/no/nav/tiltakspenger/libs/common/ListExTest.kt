package no.nav.tiltakspenger.libs.common

import io.kotest.matchers.collections.shouldHaveSize
import no.nav.tiltakspenger.libs.common.nonDistinctBy
import org.junit.jupiter.api.Test

internal class ListExTest {
    private data class Dummy(val num: Int, val text: String)

    private val liste = listOf(
        Dummy(1, "foo"),
        Dummy(2, "foo"),
        Dummy(3, "bar"),
    )

    @Test
    fun `should have no non-distinct num values`() {
        liste.nonDistinctBy { it.num }.shouldHaveSize(0)
    }

    @Test
    fun `should have two non-distinct text values`() {
        liste.nonDistinctBy { it.text }.shouldHaveSize(2)
    }
}
