package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import no.nav.tiltakspenger.libs.common.Virksomhetsnavn
import org.junit.jupiter.api.Test

internal class ArrangørTest {
    @Test
    fun `bærer begge enhetene hver for seg`() {
        val arrangør = Arrangør(
            hovedenhet = Virksomhetsnavn("Arrangør AS"),
            underenhet = Virksomhetsnavn("Arrangør AS avd Strandveien"),
        )

        arrangør.hovedenhet?.verdi shouldBe "Arrangør AS"
        arrangør.underenhet?.verdi shouldBe "Arrangør AS avd Strandveien"
    }

    /**
     * Kilden oppgir begge navn som valgfrie, og for eldre deltakelser er de ofte ukjente.
     * Team Tiltak oppgir dessuten bare én virksomhet.
     * En arrangør uten navn i det hele tatt er derfor lovlig, og skal ikke tas ned av en `require`.
     */
    @Test
    fun `begge navnene kan mangle`() {
        val ukjent = Arrangør(hovedenhet = null, underenhet = null)

        ukjent.hovedenhet shouldBe null
        ukjent.underenhet shouldBe null
    }

    /**
     * Arrangør har ingen egen `toString()`.
     * Maskeringen kommer fra [Virksomhetsnavn], og arves av den genererte `toString()`-en.
     * Det er hele poenget med å markere stedsinformasjon på typen i stedet for å overstyre per klasse som inneholder den.
     */
    @Test
    fun `toString maskerer arrangørnavnene uten at Arrangør gjør noe selv`() {
        val arrangør = Arrangør(
            hovedenhet = Virksomhetsnavn("Arrangør AS"),
            underenhet = Virksomhetsnavn("Arrangør AS avd Strandveien"),
        )

        arrangør.toString() shouldNotContain "Arrangør AS"
        arrangør.toString() shouldNotContain "Strandveien"
        arrangør.toString() shouldBe "Arrangør(hovedenhet=*****, underenhet=*****)"
    }

    /**
     * At navnet mangler er ikke stedsinformasjon, og er nyttig ved feilsøking.
     */
    @Test
    fun `toString skiller manglende navn fra maskert navn`() {
        Arrangør(hovedenhet = null, underenhet = Virksomhetsnavn("Arrangør AS avd Strandveien")).toString() shouldBe
            "Arrangør(hovedenhet=null, underenhet=*****)"
        Arrangør(hovedenhet = null, underenhet = null).toString() shouldBe
            "Arrangør(hovedenhet=null, underenhet=null)"
    }

    /**
     * Maskeringen gjelder kun `toString`.
     * Visning og sikkerlogg henter verdiene eksplisitt, på samme måte som med `Fnr.verdi`.
     */
    @Test
    fun `verdiene er fortsatt tilgjengelige gjennom feltene`() {
        Arrangør(hovedenhet = Virksomhetsnavn("Arrangør AS"), underenhet = null).hovedenhet?.verdi shouldBe "Arrangør AS"
    }
}
