package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class DeltakerstatusTest {
    /**
     * Speiler dagens guard i søknaden: tildelt plass holder, man trenger ikke ha startet.
     */
    @Test
    fun `rett til å søke krever plass, ikke oppstart`() {
        Deltakerstatus.entries.filter { it.girRettTilÅSøke }.toSet() shouldBe
            setOf(Deltakerstatus.DeltarEllerHarDeltatt, Deltakerstatus.TildeltIkkeStartet)
    }

    /**
     * Speiler dagens krav ved innvilgelse: deltakelsen må være i gang eller gjennomført.
     */
    @Test
    fun `rett til innvilgelse krever at deltakelsen er i gang eller gjennomført`() {
        Deltakerstatus.entries.filter { it.girRettTilInnvilgelse }.toSet() shouldBe
            setOf(Deltakerstatus.DeltarEllerHarDeltatt)
    }

    /**
     * Innvilgelse er strengere enn søknad.
     * Holder den invarianten, kan ingen komme i den situasjonen at de får innvilget noe de ikke kunne søkt om.
     */
    @Test
    fun `alt som kan innvilges kan også søkes om`() {
        Deltakerstatus.entries.filter { it.girRettTilInnvilgelse }.all { it.girRettTilÅSøke } shouldBe true
    }
}
