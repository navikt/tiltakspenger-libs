package no.nav.tiltakspenger.libs.person

import io.kotest.matchers.nulls.shouldBeNull
import org.junit.jupiter.api.Test

internal class PersonResponsTest {

    @Test
    fun `Test 1`() {
        val respons = PersonRespons(
            person = null,
            feil = null,
        )

        respons.person.shouldBeNull()
        respons.feil.shouldBeNull()
    }
}
