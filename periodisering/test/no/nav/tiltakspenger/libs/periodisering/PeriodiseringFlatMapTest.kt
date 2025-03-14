package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class PeriodiseringFlatMapTest {
    @Test
    fun `Fungerer som en avansert map når vi ikke har periodisering i periodisering`() {
        val periode1 = Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31))
        val periode2 = Periode(LocalDate.of(2023, 2, 1), LocalDate.of(2023, 2, 28))

        val periodisering = Periodisering(
            listOf(
                PeriodeMedVerdi("a", periode1),
                PeriodeMedVerdi("b", periode2),
            ),
        )
        val result = periodisering.flatMap { pmv ->
            listOf(PeriodeMedVerdi(pmv.verdi.uppercase(), pmv.periode))
        }
        result.size shouldBe 2
        result[0] shouldBe PeriodeMedVerdi("A", periode1)
        result[1] shouldBe PeriodeMedVerdi("B", periode2)
    }

    private data class Subperiode(
        val verdi: String,
        override val periode: Periode,
        override val opprettet: LocalDateTime,
        val subPeriode: Periodisering<String>,
    ) : Periodiserbar

    @Test
    fun `periodisering i periodisering`() {
        val periode1 = Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 14))
        val periode1Del1 = Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7))
        val periode1Del2 = Periode(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 14))

        val periode2 = Periode(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 28))
        val periode2Del1 = Periode(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 21))
        val periode2Del2 = Periode(LocalDate.of(2024, 1, 22), LocalDate.of(2024, 1, 28))

        val periode3 = Periode(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 21))
        val periode3Del1 = Periode(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 14))
        val periode3Del2 = Periode(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 21))

        val opprettet = LocalDateTime.now()
        val periodisering: Periodisering<Subperiode> = listOf(
            Subperiode(
                "a",
                periode1,
                opprettet,
                Periodisering(PeriodeMedVerdi("aa", periode1Del1), PeriodeMedVerdi("ab", periode1Del2)),
            ),
            Subperiode(
                "b",
                periode2,
                opprettet.plusSeconds(1),
                Periodisering(PeriodeMedVerdi("ba", periode2Del1), PeriodeMedVerdi("bb", periode2Del2)),
            ),
            Subperiode(
                "c",
                periode3,
                opprettet.plusSeconds(2),
                Periodisering(PeriodeMedVerdi("ca", periode3Del1), PeriodeMedVerdi("cb", periode3Del2)),
            ),
        ).toTidslinje()
        val actual: Periodisering<String> = periodisering.flatMap { pmv ->
            pmv.verdi.subPeriode.krymp(pmv.periode)
        }
        actual.perioderMedVerdi.size shouldBe 4
        actual.perioderMedVerdi shouldBe listOf(
            PeriodeMedVerdi("aa", periode1Del1),
            PeriodeMedVerdi("ca", periode3Del1),
            PeriodeMedVerdi("cb", periode3Del2),
            PeriodeMedVerdi("bb", periode2Del2),
        )
    }

    @Test
    fun `periodisering i periodisering med spesialisert flatMap`() {
        val periode1 = Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 14))
        val periode1Del1 = Periode(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7))
        val periode1Del2 = Periode(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 14))

        val periode2 = Periode(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 28))
        val periode2Del1 = Periode(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 21))
        val periode2Del2 = Periode(LocalDate.of(2024, 1, 22), LocalDate.of(2024, 1, 28))

        val periode3 = Periode(LocalDate.of(2024, 1, 7), LocalDate.of(2024, 1, 22))
        val periode3Del1 = Periode(LocalDate.of(2024, 1, 7), LocalDate.of(2024, 1, 14))
        val periode3Del2 = Periode(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 22))

        val opprettet = LocalDateTime.now()
        val periodisering: Periodisering<Subperiode> = listOf(
            Subperiode(
                "a",
                periode1,
                opprettet,
                Periodisering(PeriodeMedVerdi("aa", periode1Del1), PeriodeMedVerdi("ab", periode1Del2)),
            ),
            Subperiode(
                "b",
                periode2,
                opprettet.plusSeconds(1),
                Periodisering(PeriodeMedVerdi("ba", periode2Del1), PeriodeMedVerdi("bb", periode2Del2)),
            ),
            Subperiode(
                "c",
                periode3,
                opprettet.plusSeconds(2),
                Periodisering(PeriodeMedVerdi("ca", periode3Del1), PeriodeMedVerdi("cb", periode3Del2)),
            ),
        ).toTidslinje()
        val actual: Periodisering<String> = periodisering.flatMapPeriodisering { it.verdi.subPeriode }
        actual.perioderMedVerdi.size shouldBe 4
        actual.perioderMedVerdi shouldBe listOf(
            PeriodeMedVerdi("aa", periode1Del1.minusTilOgMed(1)),
            PeriodeMedVerdi("ca", periode3Del1),
            PeriodeMedVerdi("cb", periode3Del2),
            PeriodeMedVerdi("bb", periode2Del2.plusFraOgMed(1)),
        )
    }
}
