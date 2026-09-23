package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.januar
import no.nav.tiltakspenger.libs.periode.Periode
import no.nav.tiltakspenger.libs.periode.til
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PeriodiserbarTest {

    @Test
    fun sortering() {
        val p1 = PeriodiserbarTestimpl(
            periode = 1 til 31.januar(2025),
            opprettet = 1.januar(2025).atStartOfDay(),
        )
        val p2 = PeriodiserbarTestimpl(
            periode = 1 til 14.januar(2025),
            opprettet = 1.januar(2025).atStartOfDay().plusSeconds(1),
        )
        val p3 = PeriodiserbarTestimpl(
            periode = 15 til 31.januar(2025),
            opprettet = 1.januar(2025).atStartOfDay().plusSeconds(2),
        )

        listOf(p1, p2, p3).sorted() shouldBe listOf(p1, p2, p3)
        listOf(p2, p1, p3).sorted() shouldBe listOf(p1, p2, p3)
        listOf(p3, p2, p1).sorted() shouldBe listOf(p1, p2, p3)

        listOf(p1, p2, p3).sortedDescending() shouldBe listOf(p3, p2, p1)
        listOf(p2, p1, p3).sortedDescending() shouldBe listOf(p3, p2, p1)
        listOf(p3, p2, p1).sortedDescending() shouldBe listOf(p3, p2, p1)
    }

    private data class PeriodiserbarTestimpl(
        override val periode: Periode,
        override val opprettet: LocalDateTime,
    ) : Periodiserbar
}
