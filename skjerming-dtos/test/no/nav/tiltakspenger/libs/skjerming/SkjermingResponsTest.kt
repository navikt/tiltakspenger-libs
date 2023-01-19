package no.nav.tiltakspenger.libs.skjerming

import org.junit.jupiter.api.Test
import kotlin.test.assertNull

internal class SkjermingResponsTest {

    @Test
    fun `Test 1`() {
        val respons = SkjermingResponsDTO(
            søker = null,
            barn = null,
            feil = null,
        )

        assertNull(respons.søker)
        assertNull(respons.barn)
        assertNull(respons.feil)
    }
}
