package no.nav.tiltakspenger.libs.periode

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.dato.desember
import no.nav.tiltakspenger.libs.dato.januar
import org.junit.jupiter.api.Test

class UkeTest {

    @Test
    fun `uke 1 i 2026 starter 29 desember 2025`() {
        val periode = 1.uke(2026)

        periode.fraOgMed shouldBe 29.desember(2025)
        periode.tilOgMed shouldBe 4.januar(2026)
    }

    @Test
    fun `uke 2 i 2026 starter 5 januar`() {
        val periode = 2.uke(2026)

        periode.fraOgMed shouldBe 5.januar(2026)
        periode.tilOgMed shouldBe 11.januar(2026)
    }

    @Test
    fun `uke 53 i 2026 slutter søndag 3 januar 2027`() {
        val periode = 53.uke(2026)

        periode.fraOgMed shouldBe 28.desember(2026)
        periode.tilOgMed shouldBe 3.januar(2027)
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
