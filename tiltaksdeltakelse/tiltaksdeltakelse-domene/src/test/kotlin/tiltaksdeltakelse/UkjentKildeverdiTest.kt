package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class UkjentKildeverdiTest {
    /**
     * Tekstene er flaten visning og varsling leser, og literalen pinner dem — «hva» skal ikke drive ubemerket.
     */
    @Test
    fun `hver ukjent-type sier hva som er ukjent`() {
        Arenastatus.Ukjent("X").hva shouldBe "deltakerstatus fra Arena"
        Kometstatus.Ukjent("X", årsak = null, opprettet = testStatusOpprettet).hva shouldBe "deltakerstatus fra Komet"
        TeamTiltakstatus.Ukjent("X").hva shouldBe "avtalestatus fra Team Tiltak"
        Kometårsak.Ukjent("X").hva shouldBe "årsak fra Komet"
        Tiltakstype.Ukjent("X").hva shouldBe "tiltakskode fra kilden"
    }

    /**
     * Tiltakskoden går urørt gjennom kontrakten, så kontraktsverdien er kildens kode her.
     */
    @Test
    fun `ukjent tiltakskode svarer med samme kode i begge språk`() {
        Tiltakstype.Ukjent("NOE_HELT_NYTT").kodeIKontrakten shouldBe "NOE_HELT_NYTT"
    }

    @Test
    fun `en deltakelse der alt lot seg tolke har ingen ukjente kildeverdier`() {
        testdeltakelse().ukjenteKildeverdier shouldBe emptyList()
        testdeltakelse(fraOgMed = null).ukjenteKildeverdier shouldBe emptyList()
        testdeltakelse(tiltakstype = Tiltakstype.SomIkkeGirRett("MENTOR")).ukjenteKildeverdier shouldBe emptyList()
    }

    @Test
    fun `ukjent status og ukjent årsak er hver sin kildeverdi`() {
        val ukjentStatus = Kometstatus.Ukjent("NY_KODE", årsak = Kometårsak.Ukjent("NY_ÅRSAK"), opprettet = testStatusOpprettet)

        testdeltakelse(kildestatus = ukjentStatus).ukjenteKildeverdier shouldContainExactly
            listOf(ukjentStatus, Kometårsak.Ukjent("NY_ÅRSAK"))
    }

    @Test
    fun `kjent status med ukjent årsak gir bare årsaken`() {
        val status = Kometstatus.Kjent(Kometstatus.Type.HAR_SLUTTET, årsak = Kometårsak.Ukjent("NY_ÅRSAK"), opprettet = testStatusOpprettet)

        testdeltakelse(kildestatus = status).ukjenteKildeverdier shouldContainExactly listOf(Kometårsak.Ukjent("NY_ÅRSAK"))
    }

    @Test
    fun `kjent årsak er ingen ukjent kildeverdi`() {
        val status = Kometstatus.Kjent(Kometstatus.Type.HAR_SLUTTET, årsak = Kometårsak.Kjent(Kometstatus.Årsak.FATT_JOBB), opprettet = testStatusOpprettet)

        testdeltakelse(kildestatus = status).ukjenteKildeverdier shouldBe emptyList()
    }

    @Test
    fun `ukjent tiltakskode følger med både gjennom UkjentTiltakstype og Ugyldig`() {
        testdeltakelse(tiltakstype = Tiltakstype.Ukjent("NOE_HELT_NYTT")).ukjenteKildeverdier shouldContainExactly
            listOf(Tiltakstype.Ukjent("NOE_HELT_NYTT"))

        val ugyldigMedUkjentType = testdeltakelse(tiltakstype = Tiltakstype.Ukjent("NOE_HELT_NYTT"), fraOgMed = testSlutt, tilOgMed = testStart)
        ugyldigMedUkjentType.ukjenteKildeverdier shouldContainExactly listOf(Tiltakstype.Ukjent("NOE_HELT_NYTT"))

        val ugyldigMedKjentType = testdeltakelse(fraOgMed = testSlutt, tilOgMed = testStart)
        ugyldigMedKjentType.ukjenteKildeverdier shouldBe emptyList()
    }

    @Test
    fun `alt ukjent ved samme deltakelse samles`() {
        val deltakelse = testdeltakelse(
            kildestatus = Kometstatus.Ukjent("NY_KODE", årsak = Kometårsak.Ukjent("NY_ÅRSAK"), opprettet = testStatusOpprettet),
            tiltakstype = Tiltakstype.Ukjent("NOE_HELT_NYTT"),
        )

        deltakelse.ukjenteKildeverdier.map { it.hva } shouldContainExactly
            listOf("deltakerstatus fra Komet", "årsak fra Komet", "tiltakskode fra kilden")
    }
}
