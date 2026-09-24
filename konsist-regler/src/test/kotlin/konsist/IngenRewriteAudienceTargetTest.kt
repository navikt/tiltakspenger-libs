package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class IngenRewriteAudienceTargetTest {
    private val scope = fixtureScope("rewriteaudiencetarget")

    @Test
    fun `flagger flagget både som navngitt argument og som deklarasjon`() {
        val brudd = IngenRewriteAudienceTarget.brudd(scope).filter { it.contains("Brudd.kt") }

        brudd shouldHaveSize 3
        brudd.forEach { it shouldContain "rewriteAudienceTarget" }
    }

    @Test
    fun `KDoc, kommentarer og strengliteraler flagges ikke`() {
        val brudd = IngenRewriteAudienceTarget.brudd(scope).filter { it.contains("Ren.kt") }

        brudd.shouldBeEmpty()
    }

    @Test
    fun `unntatte filstier flagges ikke`() {
        val brudd = IngenRewriteAudienceTarget.brudd(scope, unntatteFilstier = setOf("rewriteaudiencetarget/Brudd.kt"))

        brudd.shouldBeEmpty()
    }
}
