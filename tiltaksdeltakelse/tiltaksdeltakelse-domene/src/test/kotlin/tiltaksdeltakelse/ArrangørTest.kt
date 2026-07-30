package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class ArrangørTest {
    @Test
    fun `bærer begge enhetene hver for seg`() {
        val arrangør = Arrangør(hovedenhet = "Arrangør AS", underenhet = "Arrangør AS avd Strandveien")

        arrangør.hovedenhet shouldBe "Arrangør AS"
        arrangør.underenhet shouldBe "Arrangør AS avd Strandveien"
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
     * Arrangørnavn røper hvor personen møter opp.
     * For kode 6, kode 7 og skjermede er det nettopp den opplysningen som ikke skal spres, og en `$deltakelse` i vanlig logg er den enkleste måten å miste den på.
     */
    @Test
    fun `toString maskerer arrangørnavnene`() {
        val arrangør = Arrangør(hovedenhet = "Arrangør AS", underenhet = "Arrangør AS avd Strandveien")

        arrangør.toString() shouldNotContain "Arrangør AS"
        arrangør.toString() shouldNotContain "Strandveien"
        arrangør.toString() shouldBe "Arrangør(hovedenhet=*****, underenhet=*****)"
    }

    /**
     * At navnet mangler er ikke stedsinformasjon, og er nyttig ved feilsøking.
     */
    @Test
    fun `toString skiller manglende navn fra maskert navn`() {
        Arrangør(hovedenhet = null, underenhet = "Arrangør AS avd Strandveien").toString() shouldBe
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
        Arrangør(hovedenhet = "Arrangør AS", underenhet = null).hovedenhet shouldBe "Arrangør AS"
    }
}
