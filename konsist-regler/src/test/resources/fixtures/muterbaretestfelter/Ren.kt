package fixtures.muterbaretestfelter

import org.junit.jupiter.api.Test

val toppnivåKonstant = "fast verdi"

internal class RenTest {

    private val fnr = "12345678901"

    // Muterbar tilstand bygges inne i hver test og deles aldri via felter.
    @Test
    fun `bygger tilstanden lokalt`() {
        var lokalTeller = 0
        val lokalKø = mutableListOf(fnr)
        lokalTeller += lokalKø.size
        println(lokalTeller)
    }
}

internal class RenFakeUtenTester {

    var svar: Boolean = true

    val buffer = mutableListOf<String>()
}
