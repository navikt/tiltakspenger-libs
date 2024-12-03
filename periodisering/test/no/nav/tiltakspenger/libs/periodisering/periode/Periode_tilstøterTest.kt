package no.nav.tiltakspenger.libs.periodisering.periode

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import no.nav.tiltakspenger.libs.periodisering.Periode
import no.nav.tiltakspenger.libs.periodisering.tilstøter
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class Periode_tilstøterTest {

    @Test
    fun `ingen elementer gir true`() {
        emptyList<Periode>().tilstøter().shouldBeTrue()
    }

    @Test
    fun `ett element gir true`() {
        val periode1 = Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 10))
        listOf(periode1).tilstøter().shouldBeTrue()
    }

    @Test
    fun `fraOgMed dagen etter tilOgMed tilstøter`() {
        val periode1 = Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 10))
        val periode2 = Periode(LocalDate.of(2021, 1, 11), LocalDate.of(2021, 1, 20))
        periode1.tilstøter(periode2).shouldBeTrue()
        periode2.tilstøter(periode1).shouldBeTrue()
        listOf(periode1, periode2).tilstøter().shouldBeTrue()
        listOf(periode2, periode1).tilstøter().shouldBeTrue()
    }

    @Test
    fun `fraOgMed samtidig som tilOgMed tilstøter`() {
        val periode1 = Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 10))
        val periode2 = Periode(LocalDate.of(2021, 1, 10), LocalDate.of(2021, 1, 20))
        periode1.tilstøter(periode2).shouldBeTrue()
        periode2.tilstøter(periode1).shouldBeTrue()
        listOf(periode1, periode2).tilstøter().shouldBeTrue()
        listOf(periode2, periode1).tilstøter().shouldBeTrue()
    }

    @Test
    fun `fraOgMed to dager etter tilOgMed tilstøter ikke`() {
        val periode1 = Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 10))
        val periode2 = Periode(LocalDate.of(2021, 1, 12), LocalDate.of(2021, 1, 20))
        periode1.tilstøter(periode2).shouldBeFalse()
        periode2.tilstøter(periode1).shouldBeFalse()
        listOf(periode1, periode2).tilstøter().shouldBeFalse()
        listOf(periode2, periode1).tilstøter().shouldBeFalse()
    }

    @Test
    fun `like fraOgMed usortert tilstøter`() {
        val periode1 = Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 5))
        val periode2 = Periode(LocalDate.of(2021, 1, 7), LocalDate.of(2021, 1, 7))
        val periode3 = Periode(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 1))
        listOf(periode1, periode2, periode3).tilstøter().shouldBeTrue()
    }
}
