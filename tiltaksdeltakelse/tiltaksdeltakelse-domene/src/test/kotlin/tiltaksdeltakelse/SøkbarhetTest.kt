package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SøkbarhetTest {
    private val påDato = LocalDate.of(2026, 4, 15)

    @Test
    fun `gir rett-type med status som tilsier deltakelse kan søkes på`() {
        testdeltakelse().søkbarhet(påDato) shouldBe Søkbarhet.KanSøkesPå
        testdeltakelse(tilOgMed = null).søkbarhet(påDato) shouldBe Søkbarhet.KanSøkesPå
    }

    /**
     * Tildelt plass holder — man trenger ikke ha startet.
     * Datoen avgjør: Arena `GJENNOMFORES` før startdatoen er tildelt plass, ikke deltakelse.
     */
    @Test
    fun `tildelt plass som ikke har startet kan også søkes på`() {
        val deltakelse = testdeltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.GJENNOMFORES))

        deltakelse.søkbarhet(påDato = testStart.minusDays(10)) shouldBe Søkbarhet.KanSøkesPå
    }

    /**
     * Tekstene er visningsflaten mot saksbehandler og skal ikke drive — derfor pinnes de her.
     */
    @Test
    fun `hvert nei begrunner seg med teksten som vises`() {
        testdeltakelse(fraOgMed = testSlutt, tilOgMed = testStart).søkbarhet(påDato) shouldBe
            Søkbarhet.KanIkkeSøkesPå("Datoene fra kilden henger ikke sammen, så deltakelsen kan ikke brukes i en søknad før kilden har rettet dem.")

        testdeltakelse(tiltakstype = Tiltakstype.Ukjent("NOE_HELT_NYTT")).søkbarhet(påDato) shouldBe
            Søkbarhet.KanIkkeSøkesPå("Tiltakskoden fra kilden er ikke i tabellene våre ennå, og deltakelsen må vurderes manuelt før bruker kan søke.")

        testdeltakelse(tiltakstype = Tiltakstype.SomIkkeGirRett("MENTOR")).søkbarhet(påDato) shouldBe
            Søkbarhet.KanIkkeSøkesPå("Tiltakstypen gir ikke rett til tiltakspenger.")

        testdeltakelse(kildestatus = Arenastatus.Ukjent("NY_KONTRAKTSVERDI")).søkbarhet(påDato) shouldBe
            Søkbarhet.KanIkkeSøkesPå("Statusen fra kilden er en kode vi ikke kjenner igjen ennå, og den må mappes før bruker kan søke.")

        testdeltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.TAKKET_NEI_TIL_TILBUD)).søkbarhet(påDato) shouldBe
            Søkbarhet.KanIkkeSøkesPå("Statusen hos kilden tilsier at bruker hverken deltar eller har fått tildelt plass, og da gir deltakelsen ikke rett til å søke.")
    }

    /**
     * Feilregistrert-unntaket: Arenas «ikke møtt» kan være feil, og terskelen for å få det rettet i Arena er høy.
     * Fag avklarte 2026-07-31 at «ikke møtt» ikke er deltakelse og derfor ikke gir rett til innvilgelse — men ingen har sagt at bruker skal miste retten til å *søke*.
     * Uten unntaket ville omskrivingen strammet inn søknadsflaten som en ren bieffekt av en innvilgelsesavklaring.
     */
    @Test
    fun `bruker kan søke digitalt på en Arena-deltakelse med ikke møtt`() {
        val ikkeMøtt = testdeltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.IKKE_MOTT))

        ikkeMøtt.søkbarhet(påDato).shouldBeInstanceOf<Søkbarhet.KanSøkesPåVedUnntak>().begrunnelse shouldBe
            "Arena har registrert at bruker ikke møtte. Det kan være feilregistrert, og terskelen for å få det rettet i Arena er høy, så bruker får søke og saksbehandler vurderer."
    }

    /**
     * Unntaket gjelder søknadsretten alene.
     * Innvilgelsesaksen skal stå urørt, ellers har vi gjort om fagavklaringen til det motsatte av det den sa.
     */
    @Test
    fun `unntaket flytter ikke ikke møtt over til å telle som deltakelse`() {
        val status = Arenastatus.Kjent(Arenastatus.Type.IKKE_MOTT)

        status.deltakerstatus(fraOgMed = testStart, påDato = påDato) shouldBe Deltakerstatus.IkkeDeltatt
        status.deltakerstatus(fraOgMed = testStart, påDato = påDato).deltarEllerHarDeltatt shouldBe false
    }

    /**
     * Uttrekket søknaden bygger på tar med alt `Søkbarhet` ikke sier nei til, så unntaket følger med av seg selv.
     * Det er hele poenget med at reglene bor ett sted: søknaden og manuell registrering kan ikke divergere.
     */
    @Test
    fun `deltakelsen blir med i uttrekket søknaden bygger på`() {
        val ikkeMøtt = testdeltakelse(id = "TA1", kildestatus = Arenastatus.Kjent(Arenastatus.Type.IKKE_MOTT))
        val takketNei = testdeltakelse(id = "TA2", kildestatus = Arenastatus.Kjent(Arenastatus.Type.TAKKET_NEI_TIL_TILBUD))

        val søkbare = Tiltaksdeltakelser(listOf(ikkeMøtt, takketNei)).somKildenTilsierManKanSøkePå(påDato)

        søkbare.deltakelser.map { it.id.verdi } shouldBe listOf("TA1")
    }

    /**
     * Unntaket gjelder Arena, og bare Arena.
     * Komet modellerer «ikke møtt» som en årsak på statusen i stedet for som status, og de tilfellene er allerede søkbare fordi statusen der teller som deltakelse — se TODO-en i `Kometstatus`.
     * En Komet-status som faktisk ikke gir søknadsrett skal derfor få et rent nei, ikke låne Arenas unntak.
     */
    @Test
    fun `unntaket smitter ikke over på de andre kildene`() {
        val kometUtkast = testdeltakelse(
            kildestatus = Kometstatus.Kjent(Kometstatus.Type.KLADD, årsak = null, opprettet = testStatusOpprettet),
        )

        kometUtkast.søkbarhet(påDato) shouldBe
            Søkbarhet.KanIkkeSøkesPå("Statusen hos kilden tilsier at bruker hverken deltar eller har fått tildelt plass, og da gir deltakelsen ikke rett til å søke.")
    }

    @Test
    fun `en søkbarhetsregel uten begrunnelse er en programmererfeil`() {
        shouldThrow<IllegalArgumentException> { Søkbarhet.KanSøkesPåVedUnntak("   ") }
        shouldThrow<IllegalArgumentException> { Søkbarhet.KanIkkeSøkesPå("") }
    }
}
