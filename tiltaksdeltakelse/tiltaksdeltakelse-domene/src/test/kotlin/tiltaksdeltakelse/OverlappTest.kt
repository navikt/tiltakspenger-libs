package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.periode.Overlapp
import no.nav.tiltakspenger.libs.periode.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OverlappTest {
    private val april = Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))
    private val etterpå = Periode(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
    private val førJul = Periode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))

    @Test
    fun `begge datoer fra kilden gir ja eller nei`() {
        testdeltakelse().overlapper(april) shouldBe Overlapp.Ja
        testdeltakelse().overlapper(etterpå) shouldBe Overlapp.Nei
    }

    /**
     * Med bare én dato bekrefter vi overlapp når datoen ligger i perioden, og avkrefter når den ligger på feil side.
     * Ellers kan den ukjente datoen gi begge utfall.
     */
    @Test
    fun `én dato kan bekrefte, avkrefte eller gi kanskje`() {
        testdeltakelse(tilOgMed = null).overlapper(førJul) shouldBe Overlapp.Nei
        testdeltakelse(tilOgMed = null).overlapper(april) shouldBe Overlapp.Kanskje
        testdeltakelse(tilOgMed = null).overlapper(Periode(testStart, testStart)) shouldBe Overlapp.Ja

        testdeltakelse(fraOgMed = null).overlapper(etterpå) shouldBe Overlapp.Nei
        testdeltakelse(fraOgMed = null).overlapper(april) shouldBe Overlapp.Kanskje
        testdeltakelse(fraOgMed = null).overlapper(Periode(testSlutt, testSlutt)) shouldBe Overlapp.Ja
    }

    @Test
    fun `uten datoer er svaret kanskje`() {
        testdeltakelse(fraOgMed = null, tilOgMed = null).overlapper(april) shouldBe Overlapp.Kanskje
    }

    /**
     * Datoene henger ikke sammen, så de kan hverken bekrefte eller avkrefte noe.
     */
    @Test
    fun `ugyldig svarer alltid kanskje`() {
        testdeltakelse(fraOgMed = testSlutt, tilOgMed = testStart).overlapper(april) shouldBe Overlapp.Kanskje
    }
}
