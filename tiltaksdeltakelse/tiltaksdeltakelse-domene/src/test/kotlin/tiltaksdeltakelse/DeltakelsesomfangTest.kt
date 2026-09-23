package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class DeltakelsesomfangTest {
    @Test
    fun `bærer deltakelsens og gjennomføringens verdier hver for seg`() {
        val omfang = Deltakelsesomfang(
            deltakelsesprosent = 60f,
            dagerPerUke = 3f,
            deltidsprosentPåGjennomføring = 100f,
        )

        omfang.deltakelsesprosent shouldBe 60f
        omfang.dagerPerUke shouldBe 3f
        omfang.deltidsprosentPåGjennomføring shouldBe 100f
    }

    /**
     * Kildene fyller feltene ujevnt, og for eldre Arena-deltakelser mangler de ofte helt.
     */
    @Test
    fun `alle feltene kan mangle`() {
        val ukjent = Deltakelsesomfang(
            deltakelsesprosent = null,
            dagerPerUke = null,
            deltidsprosentPåGjennomføring = null,
        )

        ukjent.deltakelsesprosent shouldBe null
        ukjent.dagerPerUke shouldBe null
        ukjent.deltidsprosentPåGjennomføring shouldBe null
    }

    /**
     * Vi validerer bevisst ikke intervall.
     * Verdier utenfor 0–100 finnes i kildene, og en `require` her ville tatt ned et helt oppslag for en verdi vi bare viser videre.
     */
    @Test
    fun `verdier utenfor normalt intervall avvises ikke`() {
        val rart = Deltakelsesomfang(
            deltakelsesprosent = 150f,
            dagerPerUke = 0f,
            deltidsprosentPåGjennomføring = -1f,
        )

        rart.deltakelsesprosent shouldBe 150f
        rart.deltidsprosentPåGjennomføring shouldBe -1f
    }
}
