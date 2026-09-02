package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class FnrGeneratorTest {

    @Test
    fun `ingen duplikater ved parallell generering`(): Unit = runBlocking {
        val generator = FnrGenerator()
        val antall = 1000

        val fnrs = (1..antall)
            .map { async(Dispatchers.Default) { generator.generer() } }
            .awaitAll()

        antall shouldBe fnrs.toSet().size
    }

    @Test
    fun `genererer bare fnr der tredje siffer er 8 eller 9`() {
        val overgangenTilNi = FnrGenerator(start = 99_999_999L)
        overgangenTilNi.generer().verdi shouldBe "00899999999"
        overgangenTilNi.generer().verdi shouldBe "00900000000"

        val overgangenTilNyPrefiks = FnrGenerator(start = 199_999_999L)
        overgangenTilNyPrefiks.generer().verdi shouldBe "00999999999"
        overgangenTilNyPrefiks.generer().verdi shouldBe "01800000000"
    }

    @Test
    fun `genererer fnr til og med øvre grense`() {
        val generator = FnrGenerator(start = 19_999_900_000L)

        generator.generer().verdi shouldBe "99999900000"
        shouldThrow<IllegalStateException> {
            generator.generer()
        }
    }
}
