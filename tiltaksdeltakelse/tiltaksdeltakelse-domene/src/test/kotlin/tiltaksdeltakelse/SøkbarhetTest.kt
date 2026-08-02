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

        testdeltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.IKKE_MOTT)).søkbarhet(påDato) shouldBe
            Søkbarhet.KanIkkeSøkesPå("Statusen hos kilden tilsier at bruker hverken deltar eller har fått tildelt plass, og da gir deltakelsen ikke rett til å søke.")
    }

    /**
     * Varianten er modellert for feilregistrert-unntaket (Arena `IKKE_MOTT`), men ingen regel produserer den før fag har avklart søknadsretten.
     * Når unntaket aktiveres i regelsettet, følger både søknaden og manuell registrering automatisk.
     */
    @Test
    fun `unntaksvarianten er modellert, men har ingen produsent ennå`() {
        val unntak = Søkbarhet.KanSøkesPåVedUnntak("Statusen kan være feilregistrert hos kilden, og bruker får søke mens saksbehandler vurderer.")

        unntak.shouldBeInstanceOf<Søkbarhet>()
    }

    @Test
    fun `en søkbarhetsregel uten begrunnelse er en programmererfeil`() {
        shouldThrow<IllegalArgumentException> { Søkbarhet.KanSøkesPåVedUnntak("   ") }
        shouldThrow<IllegalArgumentException> { Søkbarhet.KanIkkeSøkesPå("") }
    }
}
