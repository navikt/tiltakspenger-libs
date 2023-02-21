package no.nav.tiltakspenger.libs.overgangsstonad

import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class OvergangsstønadResponsTest {

    @Test
    fun `Test 1`() {
        val respons = OvergangsstønadResponsDTO(
            overgangsstønader = null,
            feil = null,
        )

        assertNull(respons.overgangsstønader)
        assertNull(respons.feil)
    }
}
