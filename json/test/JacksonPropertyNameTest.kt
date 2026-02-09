package no.nav.tiltakspenger.libs.json

import io.kotest.matchers.equals.shouldBeEqual
import org.junit.jupiter.api.Test

internal class JacksonPropertyNameTest {
    @Test
    fun `serialize - særnorske tegn`() {
        val testDTO = TestDTO(
            id = "1234",
            kanIverksette = false,
            årsak = "test",
        )
        serialize(testDTO) shouldBeEqual """{"id":"1234","kanIverksette":false,"årsak":"test"}"""
    }
}

private data class TestDTO(
    val id: String,
    val kanIverksette: Boolean,
    val årsak: String,
)
