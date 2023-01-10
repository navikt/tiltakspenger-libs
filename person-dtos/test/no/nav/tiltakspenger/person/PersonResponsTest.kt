package no.nav.tiltakspenger.person

import org.junit.jupiter.api.Test
import kotlin.test.assertNull

internal class PersonResponsTest {

    @Test
    fun `Test 1`() {
        val respons = PersonRespons(
            person = null, feil = null
        )

        assertNull(respons.person)
        assertNull(respons.feil)
    }
}
