package fixtures.muterbaretestfelter

import io.mockk.mockk
import org.junit.jupiter.api.Test

var toppnivåTeller = 0

internal class BruddTest {

    var teller = 0

    lateinit var klient: String

    val kø = mutableListOf<String>()

    private val fakeRepo = mockk<Any>()

    @Test
    fun `en test`() {
        teller += kø.size
    }
}
