package fixtures.globalmocking

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Test

internal class Ren {

    // En kommentar som nevner mockkStatic( og clearAllMocks( skal ikke flagges.
    @Test
    fun `mocker per test`() {
        val repo = mockk<Any>()
        val spion = spyk(repo)
        every { spion.hashCode() } returns 1
        val omtale = "mockkObject(Noe) i en strengliteral er ikke et kall"
        println(omtale)
    }
}
