package no.nav.tiltakspenger.person

import org.junit.jupiter.api.Test
import kotlin.test.assertNull

internal class ResponsTest {

    @Test
    fun `Test 1`() {
        val respons = Respons(
            person = null, feil = null
        )

        assertNull(respons.person)
        assertNull(respons.feil)
    }
}
