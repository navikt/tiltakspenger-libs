package no.nav.tiltakspenger.libs.konsist

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

internal class BoundaryKlasserTest {
    private val scope = fixtureScope("boundary")

    @Test
    fun `flagger boundary-navn utenfor infra-pakker, men ikke i infra-pakker eller vanlige navn`() {
        val brudd = BoundaryKlasser.brudd(scope)

        brudd shouldHaveSize 2
        brudd[0] shouldContain "data class NoeDTO"
        brudd[1] shouldContain "class SvarResponse"
    }

    @Test
    fun `tillatteFiler unntar bevisste unntak`() {
        val brudd = BoundaryKlasser.brudd(scope, tillatteFiler = setOf("boundary/Brudd.kt"))

        brudd shouldHaveSize 0
    }

    @Test
    fun `infra-segmentene er konfigurerbare`() {
        val brudd = BoundaryKlasser.brudd(scope, infraSegmenter = setOf("domene"))

        brudd.count { it.contains("Ren.kt") } shouldBe 1
    }
}
