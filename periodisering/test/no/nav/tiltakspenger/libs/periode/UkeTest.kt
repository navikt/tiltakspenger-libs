package no.nav.tiltakspenger.libs.periode

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UkeTest {

    @Test
    fun `uke 1 i 2026 starter 29 desember 2025`() {
        val periode = 1.uke(2026)

        periode.fraOgMed shouldBe LocalDate.of(2025, 12, 29)
        periode.tilOgMed shouldBe LocalDate.of(2026, 1, 4)
    }

    @Test
    fun `uke 2 i 2026 starter 5 januar`() {
        val periode = 2.uke(2026)

        periode.fraOgMed shouldBe LocalDate.of(2026, 1, 5)
        periode.tilOgMed shouldBe LocalDate.of(2026, 1, 11)
    }

    @Test
    fun `uke 53 i 2026 slutter søndag 3 januar 2027`() {
        val periode = 53.uke(2026)

        periode.fraOgMed shouldBe LocalDate.of(2026, 12, 28)
        periode.tilOgMed shouldBe LocalDate.of(2027, 1, 3)
    }

    @Test
    fun `hver uke har 7 dager`() {
        for (uke in 1..53) {
            val periode = uke.uke(2026)
            val antallDager = periode.fraOgMed.until(periode.tilOgMed).days + 1
            antallDager shouldBe 7
        }
    }

    @Test
    fun `alle uker starter på mandag`() {
        for (uke in 1..53) {
            val periode = uke.uke(2026)
            periode.fraOgMed.dayOfWeek shouldBe java.time.DayOfWeek.MONDAY
        }
    }

    @Test
    fun `alle uker i slutter på søndag`() {
        for (uke in 1..53) {
            val periode = uke.uke(2026)
            periode.tilOgMed.dayOfWeek shouldBe java.time.DayOfWeek.SUNDAY
        }
    }
}
