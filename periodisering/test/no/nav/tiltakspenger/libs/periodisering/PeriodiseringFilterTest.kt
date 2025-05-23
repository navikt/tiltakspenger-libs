package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class PeriodiseringFilterTest {
    @Test
    fun `filtrer bort miderste element`() {
        val opprettet = LocalDateTime.now()
        val elementA: PeriodeMedVerdi<Element?> = PeriodeMedVerdi(
            Element(
                "a",
                1 til 2.januar(2025),
                opprettet,
            ),
            1 til 2.januar(2025),
        )
        val elementB: PeriodeMedVerdi<Element?> = PeriodeMedVerdi(
            Element(
                "b",
                3 til 4.januar(2025),
                opprettet.plusSeconds(1),
            ),
            3 til 4.januar(2025),
        )
        val elementC: PeriodeMedVerdi<Element?> = PeriodeMedVerdi(
            Element(
                "c",
                5 til 6.januar(2025),
                opprettet.plusSeconds(2),
            ),
            5 til 6.januar(2025),
        )
        val periodisering = Periodisering(elementA, elementB, elementC)
        periodisering.filter { verdi, periode -> verdi?.verdi != "b" } shouldBe
            Periodisering(
                listOf(
                    elementA,
                    elementB.copy(verdi = null),
                    elementC,
                ),
            )
    }

    private data class Element(
        val verdi: String,
        override val periode: Periode,
        override val opprettet: LocalDateTime,
    ) : Periodiserbar
}
