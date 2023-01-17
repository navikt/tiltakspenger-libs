package no.nav.tiltakspenger.libs.skjerming

import org.junit.jupiter.api.Test
import kotlin.test.assertNull

internal class SkjermingResponsTest {

    @Test
    fun `Test 1`() {
        val respons = SkjermingRespons(
            identer = null,
            feil = null,
        )

        assertNull(respons.identer)
        assertNull(respons.feil)
    }
}
