package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class PeriodiseringMapTest {
    @Test
    fun mapVerdi() {
        val opprettet = LocalDateTime.now()
        val elementA: PeriodeMedVerdi<Element> = PeriodeMedVerdi(
            Element(
                "a",
                1 til 2.januar(2025),
                opprettet,
            ),
            1 til 2.januar(2025),
        )
        val elementB: PeriodeMedVerdi<Element> = PeriodeMedVerdi(
            Element(
                "b",
                3 til 4.januar(2025),
                opprettet.plusSeconds(1),
            ),
            3 til 4.januar(2025),
        )
        val elementC: PeriodeMedVerdi<Element> = PeriodeMedVerdi(
            Element(
                "c",
                5 til 6.januar(2025),
                opprettet.plusSeconds(2),
            ),
            5 til 6.januar(2025),
        )
        val periodisering = Periodisering(elementA, elementB, elementC)
        periodisering.mapVerdi { verdi, periode -> verdi.copy(verdi = verdi.verdi + verdi.verdi) } shouldBe
            Periodisering(
                listOf(
                    elementA.copy(verdi = elementA.verdi.copy(verdi = "aa")),
                    elementB.copy(verdi = elementB.verdi.copy(verdi = "bb")),
                    elementC.copy(verdi = elementC.verdi.copy(verdi = "cc")),
                ),
            )
    }

    private data class Element(
        val verdi: String,
        override val periode: Periode,
        override val opprettet: LocalDateTime,
    ) : Periodiserbar
}
