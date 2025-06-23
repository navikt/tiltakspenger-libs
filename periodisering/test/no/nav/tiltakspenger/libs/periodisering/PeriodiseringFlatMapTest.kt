package no.nav.tiltakspenger.libs.periodisering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.dato.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class PeriodiseringFlatMapTest {
    @Test
    fun `Fungerer som en avansert map når vi ikke har periodisering i periodisering`() {
        val periode1 = Periode(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31))
        val periode2 = Periode(LocalDate.of(2023, 2, 1), LocalDate.of(2023, 2, 28))

        val periodisering = SammenhengendePeriodisering(
            PeriodeMedVerdi("a", periode1),
            PeriodeMedVerdi("b", periode2),
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
        val subPeriode: SammenhengendePeriodisering<String>,
    ) : Periodiserbar

    @Test
    fun `periodisering i periodisering`() {
        val periode1 = 1 til 14.januar(2024)
        val periode1Del1 = 1 til 7.januar(2024)
        val periode1Del2 = 8 til 14.januar(2024)

        val periode2 = 15 til 28.januar(2024)
        val periode2Del1 = 15 til 21.januar(2024)
        val periode2Del2 = 22 til 28.januar(2024)

        val periode3 = 8 til 21.januar(2024)
        val periode3Del1 = 8 til 14.januar(2024)
        val periode3Del2 = 15 til 21.januar(2024)

        val opprettet = LocalDateTime.now(fixedClock)
        val periodisering: Periodisering<Subperiode> = listOf(
            Subperiode(
                "a",
                periode1,
                opprettet,
                SammenhengendePeriodisering(
                    PeriodeMedVerdi("aa", periode1Del1),
                    PeriodeMedVerdi("ab", periode1Del2),
                ),
            ),
            Subperiode(
                "b",
                periode2,
                opprettet.plusSeconds(1),
                SammenhengendePeriodisering(
                    PeriodeMedVerdi("ba", periode2Del1),
                    PeriodeMedVerdi("bb", periode2Del2),
                ),
            ),
            Subperiode(
                "c",
                periode3,
                opprettet.plusSeconds(2),
                SammenhengendePeriodisering(
                    PeriodeMedVerdi("ca", periode3Del1),
                    PeriodeMedVerdi("cb", periode3Del2),
                ),
            ),
        ).toTidslinje()
        val actual: SammenhengendePeriodisering<String> = periodisering.flatMap { pmv ->
            pmv.verdi.subPeriode.krymp(pmv.periode)
        } as SammenhengendePeriodisering
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
        val periodisering: SammenhengendePeriodisering<Subperiode> = nonEmptyListOf(
            Subperiode(
                "a",
                periode1,
                opprettet,
                SammenhengendePeriodisering(
                    PeriodeMedVerdi("aa", periode1Del1),
                    PeriodeMedVerdi("ab", periode1Del2),
                ),
            ),
            Subperiode(
                "b",
                periode2,
                opprettet.plusSeconds(1),
                SammenhengendePeriodisering(
                    PeriodeMedVerdi("ba", periode2Del1),
                    PeriodeMedVerdi("bb", periode2Del2),
                ),
            ),
            Subperiode(
                "c",
                periode3,
                opprettet.plusSeconds(2),
                SammenhengendePeriodisering(
                    PeriodeMedVerdi("ca", periode3Del1),
                    PeriodeMedVerdi("cb", periode3Del2),
                ),
            ),
        ).toTidslinje() as SammenhengendePeriodisering
        val actual: SammenhengendePeriodisering<String> = periodisering.flatMapPeriodisering { it.verdi.subPeriode }.tilSammenhengendePeriodisering()
        actual.perioderMedVerdi.size shouldBe 4
        actual.perioderMedVerdi shouldBe listOf(
            PeriodeMedVerdi("aa", periode1Del1.minusTilOgMed(1)),
            PeriodeMedVerdi("ca", periode3Del1),
            PeriodeMedVerdi("cb", periode3Del2),
            PeriodeMedVerdi("bb", periode2Del2.plusFraOgMed(1)),
        )
    }
}
