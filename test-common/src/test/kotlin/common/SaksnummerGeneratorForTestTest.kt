package no.nav.tiltakspenger.libs.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SaksnummerGeneratorForTestTest {
    @Test
    fun `genererer saksnummer i sekvens fra standard startpunkt`() {
        val generator = SaksnummerGeneratorForTest()

        generator.generer().verdi shouldBe "202101011001"
        generator.generer().verdi shouldBe "202101011002"
    }

    @Test
    fun `datoen sendt inn blir ignorert`() {
        val generator = SaksnummerGeneratorForTest(
            første = Saksnummer.genererSaknummer(dato = LocalDate.of(2030, 6, 1), løpenr = "2001"),
        )

        generator.generer(LocalDate.of(1999, 12, 31)).verdi shouldBe "203006012001"
        generator.generer(LocalDate.of(1999, 12, 31)).verdi shouldBe "203006012002"
    }
}
