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
     * Dagens innvilgelsesguard bygger på dette predikatet, men utfallet avgjøres i sb-api — derfor heter det fakta, ikke rettigheter.
     */
    @Test
    fun `deltar eller har deltatt gjelder nøyaktig den ene kategorien`() {
        Deltakerstatus.entries.filter { it.deltarEllerHarDeltatt }.toSet() shouldBe
            setOf(Deltakerstatus.DeltarEllerHarDeltatt)
    }

    /**
     * Innvilgelsesgrunnlaget er strengere enn søknadsretten.
     * Holder den invarianten, kan ingen komme i den situasjonen at de får innvilget noe de ikke kunne søkt om.
     */
    @Test
    fun `alt som deltar eller har deltatt kan også søkes om`() {
        Deltakerstatus.entries.filter { it.deltarEllerHarDeltatt }.all { it.girRettTilÅSøke } shouldBe true
    }
}
