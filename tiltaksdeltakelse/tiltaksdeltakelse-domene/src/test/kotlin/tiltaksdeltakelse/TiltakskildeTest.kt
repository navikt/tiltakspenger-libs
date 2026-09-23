package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class TiltakskildeTest {
    /**
     * En fjerde kilde er en reell hendelse, ikke en detalj.
     * Den ville krevd ny statusmapping, nytt id-format og oppfølging i databasetypene og DTO-ene til begge konsumentene.
     * Testen pinner antallet slik at en ny kilde ikke sniker seg inn uten at noen ser på den lista.
     */
    @Test
    fun `det finnes tre kildesystemer`() {
        Tiltakskilde.entries.toSet() shouldBe setOf(Tiltakskilde.Arena, Tiltakskilde.Komet, Tiltakskilde.TeamTiltak)
    }
}
