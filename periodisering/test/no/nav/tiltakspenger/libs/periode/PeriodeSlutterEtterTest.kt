package no.nav.tiltakspenger.libs.periode

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.periodisering.til
import org.junit.jupiter.api.Test

internal class PeriodeSlutterEtterTest {

    @Test
    fun `slutter etter dato`() {
        ((2 til 3.januar(2022)) slutterEtter 1.januar(2022)).shouldBeTrue()
        ((2 til 3.januar(2022)) slutterEtter 2.januar(2022)).shouldBeTrue()
        ((2 til 3.januar(2022)) slutterEtter 3.januar(2022)).shouldBeFalse()
        ((2 til 3.januar(2022)) slutterEtter 4.januar(2022)).shouldBeFalse()
    }

    @Test
    fun `slutter etter periode`() {
        ((2 til 3.januar(2022)) slutterEtter (1 til 1.januar(2022))).shouldBeTrue()
        ((3 til 4.januar(2022)) slutterEtter (1 til 1.januar(2022))).shouldBeTrue()
        ((2 til 3.januar(2022)) slutterEtter (2 til 3.januar(2022))).shouldBeFalse()
        ((2 til 4.januar(2022)) slutterEtter (3 til 3.januar(2022))).shouldBeTrue()
        ((2 til 3.januar(2022)) slutterEtter (4 til 5.januar(2022))).shouldBeFalse()
    }
}
