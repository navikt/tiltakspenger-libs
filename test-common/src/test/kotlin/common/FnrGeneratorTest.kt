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
    fun `genererer fnr til og med øvre grense`() {
        val generator = FnrGenerator(start = 99_999_900_000L)

        generator.generer().verdi shouldBe "99999900000"
        shouldThrow<IllegalStateException> {
            generator.generer()
        }
    }
}
