package no.nav.tiltakspenger.libs.periodisering

import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import no.nav.tiltakspenger.libs.dato.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class TomPeriodiseringTest {
    @Test
    fun `skal kunne opprette en tom periodisering`() {
        TomPeriodisering.instance<Any>() shouldBeEqual Periodisering.empty()
        TomPeriodisering.instance<String>() shouldBeEqual Periodisering.empty<Int>()
        TomPeriodisering.instance<String>().perioderMedVerdi shouldBeEqual emptyList<Int>()
    }

    @Test
    fun erSammenhengende() {
        TomPeriodisering.instance<String>().also { og ->
            og.erSammenhengende shouldBeEqual true
        }
    }

    @Test
    fun overlapper() {
        TomPeriodisering.instance<String>().also { og ->
            og.overlapper(1 til 1.januar(2021)) shouldBeEqual false
            og.overlapper(LocalDate.MIN til LocalDate.MAX) shouldBeEqual false
            og.overlapper(TomPeriodisering.instance<String>()) shouldBeEqual false
            og.overlappendePeriode(LocalDate.MIN til LocalDate.MAX) shouldBeEqual og
        }
    }

    @Test
    fun filter() {
        TomPeriodisering.instance<String>().also {
            it.filter { _, _ -> false } shouldBeEqual it
            it.filter { false } shouldBeEqual it
            it.filter { a, b -> true } shouldBeEqual it
            it.filter { true } shouldBeEqual it
        }
    }

    @Test
    fun `filter not`() {
        TomPeriodisering.instance<String>().also {
            it.filterNot { a, b -> false } shouldBeEqual it
            it.filterNot { false } shouldBeEqual it
            it.filterNot { a, b -> true } shouldBeEqual it
            it.filterNot { true } shouldBeEqual it
        }
    }

    @Test
    fun map() {
        TomPeriodisering.instance<String>().also {
            it.map { a, b -> false } shouldBeEqual it
            it.map { false } shouldBeEqual it
            it.mapVerdi { a, b -> false } shouldBeEqual it
        }
    }

    @Test
    fun flatMap() {
        TomPeriodisering.instance<String>().also { og ->
            og.flatMap { listOf(PeriodeMedVerdi("ignore-me", 1 til 1.januar(2021))) } shouldBeEqual og
        }
    }

    @Test
    fun flatMapPeriodisering() {
        TomPeriodisering.instance<String>().also { og ->
            og.flatMapPeriodisering { Periodisering(PeriodeMedVerdi("ignore-me", 1 til 1.januar(2021))) } shouldBeEqual og
        }
    }

    @Test
    fun krymp() {
        TomPeriodisering.instance<String>().also { og ->
            og.krymp(1 til 1.januar(2021)) shouldBeEqual og
        }
    }

    @Test
    fun slåSammenTilstøtendePerioder() {
        TomPeriodisering.instance<String>().also { og ->
            og.slåSammenTilstøtendePerioder() shouldBeEqual og
        }
    }

    @Test
    fun utvid() {
        TomPeriodisering.instance<String>().also { og ->
            og.utvid("verdi", 1 til 1.januar(2021)) shouldBeEqual SammenhengendePeriodisering(
                "verdi",
                1 til 1.januar(2021),
            )
        }
    }

    @Test
    fun nyPeriode() {
        TomPeriodisering.instance<String>().also { og ->
            og.nyPeriode(1 til 1.januar(2021), "default") shouldBeEqual SammenhengendePeriodisering(
                "default",
                1 til 1.januar(2021),
            )
        }
    }

    @Test
    fun setVerdiForDelperiode() {
        TomPeriodisering.instance<String>().also { og ->
            og.setVerdiForDelperiode("default", 1 til 1.januar(2021)) shouldBeEqual SammenhengendePeriodisering(
                "default",
                1 til 1.januar(2021),
            )
        }
    }

    @Test
    fun inneholderKun() {
        TomPeriodisering.instance<String>().also { og ->
            og.inneholderKun("default") shouldBeEqual true
            og.inneholderKun("kek") shouldBeEqual true
        }
    }

    @Test
    fun hentVerdiForDag() {
        TomPeriodisering.instance<String>().also { og ->
            og.hentVerdiForDag(1.januar(2021)).shouldBeNull()
        }
    }
}
