package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.libs.periode.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class TiltaksdeltakelserTest {
    private val påDato = LocalDate.of(2026, 4, 15)

    @Test
    fun `to deltakelser med samme id er en programmererfeil`() {
        shouldThrow<IllegalArgumentException> {
            Tiltaksdeltakelser(listOf(testdeltakelse(id = "TA1"), testdeltakelse(id = "TA1")))
        }
    }

    @Test
    fun `narrowing henter ut variantene uten å filtrere dem bort fra helheten`() {
        val girRett = testdeltakelse(id = "TA1")
        val ugyldig = testdeltakelse(id = "TA2", fraOgMed = testSlutt, tilOgMed = testStart)
        val ukjentStatus = testdeltakelse(id = "TA3", kildestatus = Arenastatus.Ukjent("NY_KONTRAKTSVERDI"))
        val samling = Tiltaksdeltakelser(listOf(girRett, ugyldig, ukjentStatus))

        samling.girRett shouldContainExactly listOf(girRett, ukjentStatus)
        samling.ugyldige shouldContainExactly listOf(ugyldig)
        samling.medUkjentKildestatus shouldContainExactly listOf(ukjentStatus)
        samling.deltakelser.size shouldBe 3
    }

    @Test
    fun `ukjente kildeverdier samles på tvers av deltakelsene`() {
        val samling = Tiltaksdeltakelser(
            listOf(
                testdeltakelse(id = "TA1"),
                testdeltakelse(id = "TA2", kildestatus = Arenastatus.Ukjent("NY_KONTRAKTSVERDI")),
            ),
        )

        samling.ukjenteKildeverdier shouldContainExactly listOf(Arenastatus.Ukjent("NY_KONTRAKTSVERDI"))
    }

    @Test
    fun `totalPeriode spenner fra tidligste start til seneste slutt`() {
        val samling = Tiltaksdeltakelser(
            listOf(
                testdeltakelse(id = "TA1", fraOgMed = LocalDate.of(2026, 4, 1), tilOgMed = LocalDate.of(2026, 5, 31)),
                testdeltakelse(id = "TA2"),
                testdeltakelse(id = "TA3", fraOgMed = LocalDate.of(2026, 1, 1), tilOgMed = LocalDate.of(2026, 2, 1)),
                testdeltakelse(id = "TA4", fraOgMed = LocalDate.of(2026, 12, 1), tilOgMed = LocalDate.of(2026, 12, 31)),
                testdeltakelse(id = "TA5", tilOgMed = null),
            ),
        )

        samling.perioder shouldContainExactly listOf(
            Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31)),
            Periode(testStart, testSlutt),
            Periode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)),
            Periode(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)),
        )
        samling.totalPeriode shouldBe Periode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `totalPeriode er null når ingen deltakelse har en periode`() {
        Tiltaksdeltakelser(listOf(testdeltakelse(tilOgMed = null))).totalPeriode shouldBe null
    }

    /**
     * Kanskje er med: fravær av datoer er ikke bevis på fravær av overlapp.
     */
    @Test
    fun `overlappende beholder ja og kanskje, og utelater nei`() {
        val ja = testdeltakelse(id = "TA1")
        val kanskje = testdeltakelse(id = "TA2", fraOgMed = null, tilOgMed = null)
        val nei = testdeltakelse(id = "TA3", fraOgMed = LocalDate.of(2026, 8, 1), tilOgMed = LocalDate.of(2026, 9, 30))

        val overlappende = Tiltaksdeltakelser(listOf(ja, kanskje, nei))
            .overlappende(Periode(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))

        overlappende.deltakelser shouldContainExactly listOf(ja, kanskje)
    }

    /**
     * Guarden er en ren funksjon av kildedata: typen må gi rett, statusen må være kjent, og den må gi rett til å søke.
     * De bortfiltrerte blir stående i samletypen — innsnevringen er et uttrekk, ikke et tap.
     */
    @Test
    fun `uttrekket tar bare med kjente statuser som gir rett til å søke`() {
        val søkbar = testdeltakelse(id = "TA1")
        // Takket nei, ikke «ikke møtt» — den siste er søkbar ved unntak, se SøkbarhetTest.
        val takketNei = testdeltakelse(id = "TA2", kildestatus = Arenastatus.Kjent(Arenastatus.Type.TAKKET_NEI_TIL_TILBUD))
        val ukjentStatus = testdeltakelse(id = "TA3", kildestatus = Arenastatus.Ukjent("NY_KONTRAKTSVERDI"))
        val girIkkeRett = testdeltakelse(id = "TA4", tiltakstype = Tiltakstype.SomIkkeGirRett("MENTOR"))
        val ugyldig = testdeltakelse(id = "TA5", fraOgMed = testSlutt, tilOgMed = testStart)
        val samling = Tiltaksdeltakelser(listOf(søkbar, takketNei, ukjentStatus, girIkkeRett, ugyldig))

        val uttrekk = samling.somKildenTilsierManKanSøkePå(påDato)

        uttrekk.deltakelser shouldContainExactly listOf(søkbar.shouldBeInstanceOf<Tiltaksdeltakelse.GirRett>())
        uttrekk.påDato shouldBe påDato
        samling.deltakelser.size shouldBe 5
    }

    @Test
    fun `typen kan ikke bære en deltakelse som ikke passerer guarden`() {
        val takketNei = testdeltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.TAKKET_NEI_TIL_TILBUD))
            .shouldBeInstanceOf<Tiltaksdeltakelse.GirRett>()

        shouldThrow<IllegalArgumentException> {
            TiltaksdeltakelserManKanSøkePå(deltakelser = listOf(takketNei), påDato = påDato)
        }
    }

    /**
     * Unntaket er ikke en omgåelse av guarden — det er en del av den.
     * En deltakelse som er søkbar ved unntak skal derfor kunne bæres av typen, på linje med en vanlig søkbar.
     */
    @Test
    fun `typen bærer en deltakelse som er søkbar ved unntak`() {
        val ikkeMøtt = testdeltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.IKKE_MOTT))
            .shouldBeInstanceOf<Tiltaksdeltakelse.GirRett>()

        TiltaksdeltakelserManKanSøkePå(deltakelser = listOf(ikkeMøtt), påDato = påDato)
            .deltakelser shouldContainExactly listOf(ikkeMøtt)
    }
}
