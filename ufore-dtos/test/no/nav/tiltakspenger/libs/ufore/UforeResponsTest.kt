package no.nav.tiltakspenger.libs.ufore

import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class UforeResponsTest {

    @Test
    fun `Test 1`() {
        val respons = UforeResponsDTO(
            uføregrad = null,
            feil = null,
        )

        assertNull(respons.uføregrad)
        assertNull(respons.feil)
    }
}
