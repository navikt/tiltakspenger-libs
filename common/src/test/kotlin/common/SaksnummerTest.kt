package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal class SaksnummerTest {

    @Test
    fun prefiks() {
        val saksnummer = Saksnummer.genererSaknummer(dato = LocalDate.of(2021, 1, 1), løpenr = "1001")
        saksnummer.verdi shouldBe "202101011001"
        saksnummer.løpenr shouldBe "1001"
        saksnummer.prefiks shouldBe "20210101"
        saksnummer.dato shouldBe LocalDate.of(2021, 1, 1)
    }

    @Test
    fun `inkrementerer prod med 1`() {
        val saksnummer = Saksnummer.genererSaknummer(dato = LocalDate.of(2021, 1, 1), løpenr = "0001")
        saksnummer.verdi shouldBe "202101010001"
        saksnummer.løpenr shouldBe "0001"
        saksnummer.prefiks shouldBe "20210101"
        saksnummer.dato shouldBe LocalDate.of(2021, 1, 1)
        val neste = saksnummer.nesteSaksnummer()
        neste.verdi shouldBe "202101010002"
        neste.løpenr shouldBe "0002"
        neste.prefiks shouldBe "20210101"
        neste.dato shouldBe LocalDate.of(2021, 1, 1)
    }

    @Test
    fun `kan opprette saksnummer med dato og løpenummer som int`() {
        val saksnummer = Saksnummer(dato = LocalDate.of(2021, 1, 1), løpenr = 1001)

        saksnummer.verdi shouldBe "202101011001"
        saksnummer.løpenr shouldBe "1001"
        saksnummer.prefiks shouldBe "20210101"
        saksnummer.dato shouldBe LocalDate.of(2021, 1, 1)
    }

    @Test
    fun `kan generere saksnummer fra clock`() {
        val saksnummer = Saksnummer.genererSaknummer(clock = fixedClockAt(LocalDate.of(2024, 2, 29)), løpenr = "1001")

        saksnummer.verdi shouldBe "202402291001"
        saksnummer.løpenr shouldBe "1001"
        saksnummer.prefiks shouldBe "20240229"
        saksnummer.dato shouldBe LocalDate.of(2024, 2, 29)
    }

    @Test
    fun `toString returnerer verdi`() {
        val saksnummer = Saksnummer.genererSaknummer(dato = LocalDate.of(2021, 1, 1), løpenr = "1001")

        saksnummer.toString() shouldBe saksnummer.verdi
    }

    @Test
    fun `feiler dersom saksnummer er for kort`() {
        shouldThrowWithMessage<IllegalArgumentException>("Saksnummer må være 12 tegn eller lengre") {
            Saksnummer("20210101100")
        }
    }

    @Test
    fun `feiler dersom løpenummer er 0`() {
        shouldThrowWithMessage<IllegalArgumentException>("Løpenummer må være lik eller større enn 1001") {
            Saksnummer("202101010000")
        }
    }

    @Test
    fun `feiler før require dersom verdi er kortere enn prefikset`() {
        shouldThrow<IndexOutOfBoundsException> {
            Saksnummer("2021010")
        }
    }

    @Test
    fun `feiler dersom prefiks ikke er en gyldig dato`() {
        shouldThrow<DateTimeParseException> {
            Saksnummer("202113011001")
        }
    }

    @Test
    fun `feiler dersom løpenummer ikke er numerisk`() {
        shouldThrow<NumberFormatException> {
            Saksnummer("20210101ABCD")
        }
    }
}
