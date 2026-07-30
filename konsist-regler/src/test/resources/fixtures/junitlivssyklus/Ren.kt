package fixtures.junitlivssyklus

import org.junit.jupiter.api.Test

internal class Ren {

    // Rigg bygges inne i hver test, ikke i en @BeforeEach eller @BeforeAll.
    @Test
    fun `bygger konteksten selv`() {
        val kontekst = "alt lokalt, ingen org.junit.jupiter.api.TestInstance i en streng teller"
        println(kontekst)
    }
}
