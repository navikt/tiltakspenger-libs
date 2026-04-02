package no.nav.tiltakspenger.libs.periode

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import no.nav.tiltakspenger.libs.dato.januar
import org.junit.jupiter.api.Test

internal class PeriodeStarterEtterTest {

    @Test
    fun `starter etter dato`() {
        ((2 til 3.januar(2022)) starterEtter 1.januar(2022)).shouldBeTrue()
        ((2 til 3.januar(2022)) starterEtter 2.januar(2022)).shouldBeFalse()
        ((2 til 3.januar(2022)) starterEtter 3.januar(2022)).shouldBeFalse()
        ((2 til 3.januar(2022)) starterEtter 4.januar(2022)).shouldBeFalse()
    }

    @Test
    fun `starter etter periode`() {
        ((2 til 3.januar(2022)) starterEtter (1 til 1.januar(2022))).shouldBeTrue()
        ((3 til 4.januar(2022)) starterEtter (1 til 1.januar(2022))).shouldBeTrue()
        ((2 til 3.januar(2022)) starterEtter (2 til 3.januar(2022))).shouldBeFalse()
        ((2 til 3.januar(2022)) starterEtter (3 til 3.januar(2022))).shouldBeFalse()
        ((2 til 3.januar(2022)) starterEtter (4 til 5.januar(2022))).shouldBeFalse()
    }
}
