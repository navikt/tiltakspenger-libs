package no.nav.tiltakspenger.libs.skjerming

import org.junit.jupiter.api.Test
import kotlin.test.assertNull

internal class SkjermingResponsTest {

    @Test
    fun `Test 1`() {
        val respons = SkjermingResponsDTO(
            skjerming = null,
            feil = null,
        )

        assertNull(respons.skjerming)
        assertNull(respons.feil)
    }
}
